package com.vora.reservation.application.service;


import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.OfferNotFoundException;
import com.vora.reservation.application.exception.OfferNotAddressedToThisDriverException;
import com.vora.reservation.application.exception.PaymentNotInitiatedException;
import com.vora.reservation.application.exception.ReservationNotAcceptedException;
import com.vora.reservation.application.exception.ReservationNotStartedException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.domain.enums.OfferStatus;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.enums.TurnStatus;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.domain.model.ReservationOffer;
import com.vora.reservation.domain.model.Turn;
import com.vora.reservation.infrastructure.persistence.ReservationOfferRepository;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import com.vora.reservation.infrastructure.persistence.TurnRepository;
import com.vora.reservation.infrastructure.notification.NotificationService;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import com.vora.reservation.infrastructure.security.VoraRole;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j

public class DriveTripService {

    private final TurnRepository turnRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationOfferRepository reservationOfferRepository;
    private final OfferService offerService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    // ---------- Start (chauffeur affecté uniquement) ----------

    /**
     * Démarre la course d'un passager sur lequel le chauffeur est affecté.
     * Seul le chauffeur du Turn concerné peut appeler cette route.
     * Statut : ACCEPTÉE -> EN_COURS.
     */
    @Transactional
    public Reservation startTrip(AuthenticatedUser requester, UUID reservationId) {
        requireDriver(requester, "Seul un chauffeur authentifié peut démarrer une course.");

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        Turn turn = reservation.getTurn();
        if (turn == null) {
            throw new ReservationNotAcceptedException(
                    "COURSE_NON_ATTRIBUEE: cette réservation n'est pas encore affectée à un chauffeur.");
        }

        if (!turn.getDriverId().equals(requester.driverId())) {
            throw new ForbiddenOperationException(
                    "Vous n'êtes pas le chauffeur affecté à cette course.");
        }

        // La route accepte une réservation ACCEPTÉE ou déjà EN_COURS pour idempotence.
        if (reservation.getStatus() != ReservationStatus.ACCEPTEE
                && reservation.getStatus() != ReservationStatus.EN_COURS) {
            throw new ReservationNotStartedException(
                    "COURSE_NON_DEMARREE: la réservation n'est pas dans un état démarrable.");
        }

        if (reservation.getStatus() == ReservationStatus.EN_COURS) {
            return reservation; // idempotence
        }

        // Vérification + verrou sur l'offre acceptée dissipée par ce chauffeur.
        ReservationOffer offer = reservationOfferRepository.findByReservationIdAndStatus(
                        reservationId, OfferStatus.ACCEPTEE)
                .stream()
                .findFirst()
                .orElse(null);

        if (offer == null) {
            throw new OfferNotFoundException(
                    "OFFRE_INTROUVABLE: aucune offre acceptée pour cette réservation.");
        }

        if (!offer.getDriverId().equals(requester.driverId())) {
            throw new OfferNotAddressedToThisDriverException(
                    "OFFRE_NON_ADRESSEE: cette offre n'a pas été diffusée à ce chauffeur.");
        }

        reservation.start();
        reservationRepository.save(reservation);

        turn.startBoarding();
        turnRepository.save(turn);

        // Phase 9 : notification webhook front — chauffeur a démarré la course.
        try {
            notificationService.notifyDriverStarted(reservationId, turn.getId());
        } catch (Exception e) {
            log.warn("Notification démarrage non délivrée pour réservation {} : {}", reservationId, e.getMessage());
        }

        log.info("Chauffeur {} a démarré la course {} (Turn {})", requester.driverId(), reservationId, turn.getId());
        return reservation;
    }

    // ---------- Arrival (client propriétaire uniquement) ----------

    /**
     * Confirmation d'arrivée par le client propriétaire de la réservation.
     * Seule une réservation EN_COURS peut être confirmée.
     * Cela libère une place dans le Turn, initie le paiement, puis relance
     * activement le matching pour ce chauffeur (cadrage §6 étapes 11/11bis/12,
     * §5.1).
     *
     * <p>Le paiement est initié AVANT la relance de matching car c'est l'étape 12
     * du workflow. Si Node Auth & Payment est indisponible, on logue l'incident
     * et on marque la réservation PAIEMENT_ECHOUE en local pour ne pas laisser
     * la réservation durablement dans un statut PAIEMENT_EN_COURS sans issue.
     */
    @Transactional
    public Reservation confirmArrival(AuthenticatedUser requester, UUID reservationId) {
        if (requester == null || requester.role() != VoraRole.CLIENT || requester.userId() == null) {
            throw new ForbiddenOperationException("Seul le client propriétaire peut confirmer son arrivée.");
        }

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (!reservation.getClientId().equals(requester.userId())) {
            throw new ForbiddenOperationException("Vous ne pouvez confirmer que vos propres réservations.");
        }

        if (reservation.getStatus() != ReservationStatus.EN_COURS) {
            throw new ReservationNotStartedException(
                    "COURSE_NON_DEMARREE: la réservation n'est pas encore en cours de transport.");
        }

        reservation.confirmArrival();
        reservationRepository.save(reservation);

        // Phase 9 : notification webhook front — arrivée confirmée.
        try {
            notificationService.notifyArrivalConfirmed(reservationId);
        } catch (Exception e) {
            log.warn("Notification arrivée confirmée non délivrée pour réservation {} : {}", reservationId, e.getMessage());
        }

        Turn turn = reservation.getTurn();
        if (turn != null) {
            turn.releaseSeat();

            log.info("Passager {} a confirmé l'arrivée (Turn {}, charge libérée: {})",
                    reservationId, turn.getId(), turn.getCurrentLoad());

            // Étape 12 : initiation du paiement AVANT la relance de matching.
            initiatePaymentSilently(reservationId);

            // Étape 11bis : relance active du matching.
            boolean candidateFound = relaunchMatchingForDriver(turn.getDriverId());

            // Étape 14 : clôture uniquement si plus personne à bord
            // ET aucun nouveau candidat compatible trouvé lors de la relance.
            if (turn.getCurrentLoad() == 0 && !candidateFound) {
                turn.close();
            }

            turnRepository.save(turn);
        } else {
            log.warn("Réservation confirmée sans Turn associé ({} )", reservationId);
            // Sans Turn, pas de relance possible ; on initie quand même le paiement.
            initiatePaymentSilently(reservationId);
        }

        return reservation;
    }

    // ---------- Relance automatique du matching ----------

    /**
     * Après la libération d'une place, relance active le matching pour
     * ré-attribuer un candidat compatible parmi les réservations EN_ATTENTE.
     * Découplage : la recherche de candidats respectera un contrat à définir
     * avec Django Geo ; pour l'instant, elle se base sur les réservations
     * en attente compatibles spatialement.
     */
    private boolean relaunchMatchingForDriver(Long driverId) {
        Turn currentTurn = turnRepository.findByDriverIdAndStatusInForUpdate(
                        driverId, List.of(TurnStatus.OUVERT, TurnStatus.COMPLET, TurnStatus.EN_COURS))
                .orElse(null);

        if (currentTurn == null) {
            log.debug("Aucun Turn actif pour le chauffeur {} lors de la relance matching", driverId);
            return false;
        }

        if (!currentTurn.hasFreeSeat()) {
            log.debug("Turn {} est complet, pas de relance de matching nécessaire", currentTurn.getId());
            return false;
        }

        List<Reservation> pendingCandidates = reservationRepository.findByStatus(ReservationStatus.EN_ATTENTE);

        if (pendingCandidates.isEmpty()) {
            log.debug("Aucune réservation EN_ATTENTE à réattribuer pour le chauffeur {}", driverId);
            return false;
        }

        // TODO (bloquant, cf. ReservationService) : cette sélection ignore la
        // compatibilité spatiale réelle (verify-destination / optimize/turn côté
        // Django Geo, cadrage §5.1 étape 11 bis) tant que le contrat exact n'est
        // pas clarifié avec l'équipe Django. En l'état, elle diffuse au premier
        // candidat EN_ATTENTE sans garantie qu'il soit dans le corridor de ce
        // chauffeur — à ne pas considérer comme la relance de matching finale.
        log.info("Relance de matching pour le chauffeur {} ({} réservation(s) en attente compatibles)",
                driverId, pendingCandidates.size());

        Reservation firstCandidate = pendingCandidates.get(0);
        List<Long> candidateDriverIds = List.of(driverId);
        offerService.diffuseOffers(firstCandidate, candidateDriverIds);
        return true;
    }

    // ---------- Paiement (Phase 8) ----------

    /**
     * Initiation silencieuse du paiement après arrivée confirmée.
     *
     * <p>En cas d'indisponibilité de Node Auth & Payment, on logue l'erreur et
     * on marque la réservation PAIEMENT_ECHOUE localement pour ne pas laisser
     * la réservation bloquée en PAIEMENT_EN_COURS sans issue.
     * Le client pourra retenter manuellement via POST
     * /api/v1/reservations/{id}/payment.
     */
    private void initiatePaymentSilently(UUID reservationId) {
        try {
            paymentService.initiatePayment(reservationId);
        } catch (PaymentNotInitiatedException e) {
            log.warn("Paiement non initié pour réservation {} : {}", reservationId, e.getMessage());
        } catch (Exception e) {
            log.error("Échec initiation paiement réservation {} (Node indisponible ou erreur) : {}",
                    reservationId, e.getMessage());
            markPaymentFailedLocally(reservationId);
        }
    }

    /**
     * Marque localement la réservation comme PAIEMENT_ECHOUE sans appeler Node.
     * À utiliser quand le paiement ne peut pas être initié.
     */
    private void markPaymentFailedLocally(UUID reservationId) {
        try {
            Reservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
            if (reservation != null
                    && reservation.getStatus() == ReservationStatus.ARRIVEE_CONFIRMEE) {
                reservation.markPaymentFailed();
                reservationRepository.save(reservation);
                log.info("Paiement marqué ÉCHOUÉ localement pour réservation {} (initiation impossible)", reservationId);
            }
        } catch (Exception e) {
            log.error("Échec marquage échec local réservation {} : {}", reservationId, e.getMessage());
        }
    }

    // ---------- Helper ----------

    private void requireDriver(AuthenticatedUser requester, String message) {
        if (requester == null || requester.role() != VoraRole.CHAUFFEUR || requester.driverId() == null) {
            throw new ForbiddenOperationException(message);
        }
    }

}
