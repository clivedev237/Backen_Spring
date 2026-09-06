package com.vora.reservation.application.service;

import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.OfferAlreadyResolvedException;
import com.vora.reservation.application.exception.OfferExpiredException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.application.exception.ReservationOfferNotFoundException;
import com.vora.reservation.application.exception.TurnFullException;
import com.vora.reservation.domain.enums.OfferStatus;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.enums.TurnStatus;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.domain.model.ReservationOffer;
import com.vora.reservation.domain.model.Turn;
import com.vora.reservation.infrastructure.persistence.ReservationOfferRepository;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import com.vora.reservation.infrastructure.persistence.TurnRepository;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import com.vora.reservation.infrastructure.security.VoraRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfferService {
    private final ReservationOfferRepository offerRepository;
    private final ReservationRepository reservationRepository;
    private final TurnRepository turnRepository;

    /**
     * Diffuse une offre à chaque chauffeur candidat (cadrage §6, étape 5).
     * Ne fait rien si la liste est vide (cadrage §5.1, règle 38 : la
     * réservation reste EN_ATTENTE tant qu'aucun candidat n'est trouvé).
     * Idempotent sur le statut de la réservation : rappelable plusieurs fois
     * (relance après débarquement, cadrage §5.1 règle 37) sans effet de bord
     * si elle est déjà DIFFUSÉE.
     */
    @Transactional
    public List<ReservationOffer> diffuseOffers(Reservation reservation, List<Long> candidateDriverIds) {
        if (candidateDriverIds == null || candidateDriverIds.isEmpty()) {
            return List.of();
        }

        List<ReservationOffer> offers = candidateDriverIds.stream()
                .map(driverId -> offerRepository.save(ReservationOffer.sendTo(reservation, driverId)))
                .toList();

        reservation.diffuse();
        reservationRepository.save(reservation);

        log.info("Réservation {} diffusée à {} chauffeur(s) candidat(s)", reservation.getId(), offers.size());
        return offers;
    }

    /**
     * Offres en attente pour un chauffeur (endpoint {@code GET
     * /api/v1/driver/reservation-offers}, cadrage §13). Vérification
     * paresseuse de l'expiration à la lecture (décision de cadrage Phase 5) :
     * toute offre EN_ATTENTE dont le délai est dépassé est marquée EXPIRÉE et
     * exclue du résultat, sans job planifié dédié.
     */
    @Transactional
    public List<ReservationOffer> listPendingOffersForDriver(AuthenticatedUser requester) {
        requireDriver(requester);

        OffsetDateTime now = OffsetDateTime.now();
        List<ReservationOffer> pending = offerRepository.findByDriverIdAndStatus(
                requester.driverId(), OfferStatus.EN_ATTENTE);

        return pending.stream()
                .filter(offer -> {
                    if (offer.isExpired(now)) {
                        offer.setStatus(OfferStatus.EXPIREE);
                        offer.setExpiredAt(now);
                        offerRepository.save(offer);
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    /**
     * Acceptation atomique d'une offre (cadrage §8.1). Verrouille la
     * réservation ciblée, PUIS le Turn du chauffeur si un Turn actif existe
     * déjà — toujours dans cet ordre (Reservation puis Turn), pour éviter tout
     * deadlock avec un futur appelant qui verrouillerait dans le même sens.
     * <p>
     * Le verrou sur la réservation arbitre la concurrence entre plusieurs
     * offres visant la MÊME réservation (premier gagnant, cadrage §8.1). Le
     * verrou sur le Turn arbitre, en plus, la concurrence entre deux
     * acceptations visant des réservations DIFFÉRENTES mais le même Turn
     * (dernière place disponible) — cas non couvert explicitement par le
     * cadrage mais nécessaire pour ne jamais dépasser Nmax=4.
     */
    @Transactional
    public Reservation acceptOffer(AuthenticatedUser requester, UUID offerId) {

        requireDriver(requester);

        ReservationOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ReservationOfferNotFoundException(offerId));

        if (!offer.getDriverId().equals(requester.driverId())) {
            throw new ForbiddenOperationException("Cette offre n'a pas été diffusée à ce chauffeur.");
        }

        UUID reservationId = offer.getReservation().getId();

        // 1. Verrou sur la réservation en premier (ordre fixe, cadrage §8.1).
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // Si la réservation n'est plus DIFFUSÉE, quelqu'un a déjà conclu
        // (un autre chauffeur a gagné, ou elle a été annulée entre-temps).
        if (reservation.getStatus() != ReservationStatus.DIFFUSEE) {
            throw new OfferAlreadyResolvedException(
                    "COURSE_DEJA_ATTRIBUEE : cette réservation n'est plus disponible (statut actuel : "
                            + reservation.getStatus() + ").");
        }

        // Relire l'offre à l'intérieur du verrou pour éviter de travailler sur un
        // état obsolète (ex. refusée entre le chargement initial et l'acquisition
        // du verrou).
        ReservationOffer freshOffer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ReservationOfferNotFoundException(offerId));

        if (freshOffer.getStatus() != OfferStatus.EN_ATTENTE) {
            throw new OfferAlreadyResolvedException(
                    "COURSE_DEJA_ATTRIBUEE : cette offre n'est plus en attente (statut actuel : "
                            + freshOffer.getStatus() + ").");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (freshOffer.isExpired(now)) {
            freshOffer.setStatus(OfferStatus.EXPIREE);
            freshOffer.setExpiredAt(now);
            offerRepository.save(freshOffer);
            throw new OfferExpiredException(
                    "OFFRE_EXPIREE : le délai de 5 minutes est dépassé, cette offre ne peut plus être acceptée.");
        }

        // 2. Résolution du Turn : insertion dans le Turn actif du chauffeur
        // (OUVERT, COMPLET — se rouvrira si une place se libère —, ou déjà
        // EN_COURS puisqu'un passager déjà à bord n'empêche pas d'en accepter
        // un second tant que Nmax n'est pas atteint, cadrage §5/§5.4), ou
        // création d'un nouveau Turn si aucun n'existe (cadrage §6, étape 6).
        Turn candidateTurn = turnRepository.findByDriverIdAndStatusIn(requester.driverId(),
                        List.of(TurnStatus.OUVERT, TurnStatus.COMPLET, TurnStatus.EN_COURS))
                .stream()
                .findFirst()
                .orElse(null);

        Turn turn;
        int sequenceIndex;
        if (candidateTurn != null) {
            // 2a. Turn existant : verrou pessimiste (ordre fixe : après la
            // réservation, jamais avant — cadrage §8.1 étendu). L'id est
            // capturé dans une variable dédiée (non réassignée) car une
            // variable réaffectée ne peut pas être référencée dans le lambda
            // ci-dessous (effectively final requis).
            UUID turnId = candidateTurn.getId();
            turn = turnRepository.findByIdForUpdate(turnId)
                    .orElseThrow(() -> new IllegalStateException("Turn " + turnId + " introuvable sous verrou."));

            if (!turn.hasFreeSeat()) {
                // Décision de cadrage Phase 5 : l'offre reste EN_ATTENTE, pas
                // d'invalidation — le chauffeur pourra retenter si une place
                // se libère (débarquement, annulation).
                throw new TurnFullException(
                        "TURN_COMPLET : le Turn " + turnId + " s'est rempli entre-temps.");
            }

            sequenceIndex = turn.getCurrentLoad();
            turn.incrementLoad();
        } else {
            // 2b. Aucun Turn actif : création (trajectoryId non renseigné,
            // décision de cadrage Phase 5 — à compléter une fois le contrat
            // Django Geo clarifié, Phase 3/4). Limite connue : pas de verrou
            // possible sur une ligne qui n'existe pas encore, voir Javadoc
            // de classe.
            turn = Turn.open(requester.driverId(), null, 4);
            sequenceIndex = 0;
            turn.incrementLoad();
        }
        turnRepository.save(turn);

        // 3. Attribution : la réservation ET l'offre gagnante passent ACCEPTÉE.
        reservation.assignToTurn(turn, sequenceIndex);
        reservationRepository.save(reservation);

        freshOffer.accept();
        offerRepository.save(freshOffer);

        // 4. Invalidation des autres offres EN_ATTENTE pour cette réservation
        // (cadrage §8.1 : "les autres offres deviennent invalides").
        offerRepository.findByReservationIdAndStatus(reservationId, OfferStatus.EN_ATTENTE).stream()
                .filter(other -> !other.getId().equals(freshOffer.getId()))
                .forEach(other -> {
                    other.invalidate();
                    offerRepository.save(other);
                });

        log.info("Offre {} acceptée par le chauffeur {} — réservation {} insérée dans le Turn {} (position {})",
                offerId, requester.driverId(), reservationId, turn.getId(), sequenceIndex);

        return reservation;

    }

    /**
     * Refus explicite d'une offre par le chauffeur (ajout Phase 5, endpoint
     * non documenté dans le cadrage v2.1 — voir décision de cadrage). Ne
     * nécessite aucun verrou : ne mute ni la réservation ni le Turn.
     */
    @Transactional
    public void declineOffer(AuthenticatedUser requester, UUID offerId) {
        requireDriver(requester);

        ReservationOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ReservationOfferNotFoundException(offerId));

        if (!offer.getDriverId().equals(requester.driverId())) {
            throw new ForbiddenOperationException("Cette offre n'a pas été diffusée à ce chauffeur.");
        }

        if (offer.getStatus() != OfferStatus.EN_ATTENTE) {
            throw new OfferAlreadyResolvedException(
                    "Cette offre n'est plus en attente (statut actuel : " + offer.getStatus() + ").");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (offer.isExpired(now)) {
            offer.setStatus(OfferStatus.EXPIREE);
            offer.setExpiredAt(now);
            offerRepository.save(offer);
            throw new OfferExpiredException(
                    "OFFRE_EXPIREE : le délai de 5 minutes est dépassé, cette offre ne peut plus être refusée.");
        }

        offer.decline();
        offerRepository.save(offer);
    }

    private void requireDriver(AuthenticatedUser requester) {
        if (requester == null || requester.role() != VoraRole.CHAUFFEUR || requester.driverId() == null) {
            throw new ForbiddenOperationException("Seul un chauffeur authentifié peut réaliser cette action.");
        }
    }
}
