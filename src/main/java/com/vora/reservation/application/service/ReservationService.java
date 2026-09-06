package com.vora.reservation.application.service;

import com.vora.reservation.api.dto.CreateReservationRequest;
import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.infrastructure.client.geo.GeoClient;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import com.vora.reservation.infrastructure.security.VoraRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    private final ReservationRepository reservationRepository;

    // Conservé pour la suite (Phase 3/4 seront réécrites avec le vrai contrat
    // Django) : injecté mais non utilisé pour l'instant, voir TODO ci-dessous.
    private final GeoClient geoClient;

    /**
     * Crée une réservation pour le client authentifié. Seul un CLIENT peut
     * créer une réservation ; le {@code clientId} provient exclusivement de
     * l'identité posée par le Gateway, jamais du corps de la requête.
     * <p>
     * <b>TODO (bloquant, cf. échange sur le contrat réel Django Geo)</b> :
     * l'appel à {@code verify-destination} puis {@code optimize/turn} a été
     * retiré temporairement. Le contrat réel obtenu montre que
     * {@code verify-destination} vérifie UN SEUL {@code driverId} (pas de
     * recherche multi-chauffeurs, pas de {@code pickup}), et que
     * {@code optimize/turn} ne prend que {@code driverId}/{@code zoneId}
     * sans aucune donnée de réservation. Deux points sont à clarifier avec
     * l'équipe Django avant de rebrancher cette étape :
     * <ol>
     *   <li>Comment Réservation obtient la liste des {@code driverId}
     *   candidats à tester (aucun endpoint de découverte "chauffeurs actifs
     *   à proximité" dans le contrat fourni) ;</li>
     *   <li>Comment {@code optimize/turn} accède aux réservations en attente
     *   sans qu'on les lui transmette (lecture directe de la base partagée ?
     *   contredit le §3 du cadrage).</li>
     * </ol>
     * En attendant, la réservation est créée directement en EN_ATTENTE
     * (cadrage §5.1 : c'est de toute façon le comportement attendu tant
     * qu'aucun chauffeur compatible n'est trouvé).
     */
    @Transactional
    public Reservation create(AuthenticatedUser requester, CreateReservationRequest request) {
        requireRole(requester, VoraRole.CLIENT,
                "Seul un client peut créer une réservation.");

        Reservation reservation = Reservation.create(
                requester.userId(),
                request.pickup().latitude(),
                request.pickup().longitude(),
                request.pickup().precision(),
                request.destination().latitude(),
                request.destination().longitude(),
                request.destination().address(),
                request.proposedPrice(),
                request.paymentMethod()
        );

        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public Reservation getById(AuthenticatedUser requester, UUID id) {
        if (requester.role() == VoraRole.CHAUFFEUR) {
            throw new ForbiddenOperationException(
                    "La consultation d'une réservation par un chauffeur sera disponible à partir de la diffusion des offres.");
        }

        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        if (requester.role() == VoraRole.CLIENT && !reservation.getClientId().equals(requester.userId())) {
            throw new ForbiddenOperationException("Vous ne pouvez consulter que vos propres réservations.");
        }

        return reservation;
    }

    @Transactional(readOnly = true)
    public Page<Reservation> list(AuthenticatedUser requester,
                                  ReservationStatus statusFilter,
                                  Long clientIdFilter,
                                  Pageable pageable) {
        if (requester.role() == VoraRole.CHAUFFEUR) {
            throw new ForbiddenOperationException(
                    "La consultation des réservations par un chauffeur sera disponible à partir de la diffusion des offres.");
        }

        Long effectiveClientId = requester.role() == VoraRole.ADMIN ? clientIdFilter : requester.userId();

        if (effectiveClientId != null && statusFilter != null) {
            return reservationRepository.findByClientIdAndStatus(effectiveClientId, statusFilter, pageable);
        }
        if (effectiveClientId != null) {
            return reservationRepository.findByClientId(effectiveClientId, pageable);
        }
        if (statusFilter != null) {
            return reservationRepository.findByStatus(statusFilter, pageable);
        }
        return reservationRepository.findAll(pageable);
    }

    private void requireRole(AuthenticatedUser requester, VoraRole expected, String message) {
        if (requester == null || requester.role() != expected) {
            throw new ForbiddenOperationException(message);
        }
    }
}
