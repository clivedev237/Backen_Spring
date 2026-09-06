package com.vora.reservation.api.controller;

import com.vora.reservation.api.dto.DriverLocationRequest;
import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.ReservationNotAcceptedException;
import com.vora.reservation.application.exception.ReservationNotStartedException;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.enums.TurnStatus;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.domain.model.Turn;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import com.vora.reservation.infrastructure.persistence.TurnRepository;
import com.vora.reservation.infrastructure.realtime.DriverLocationPublisher;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint côté chauffeur pour l'envoi de sa position GPS (Phase 7, option A).
 *
 * <p>Le chauffeur publie sa position par REST {@code POST
 * /api/v1/driver/reservations/{id}/location}. La diffusion vers le topic
 * STOMP {@code /topic/reservations/{id}/driver-location} est assurée par le
 * service {@link DriverLocationPublisher} injecté ici.
 *
 * <p>Règles métier (cadrage §5.1, §13) :
 * <ul>
 *   <li>Seul le chauffeur authentifié du Turn concerné peut publier pour une
 *       réservation.</li>
 *   <li>La réservation doit être EN_COURS (le passager est déjà à bord).</li>
 *   <li>La réservation doit appartenir au Turn du chauffeur (turn != null,
 *       turn.driverId == driverId).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/driver/reservations")
@RequiredArgsConstructor
@Slf4j
public class DriverLocationController {

    private final ReservationRepository reservationRepository;
    private final TurnRepository turnRepository;
    private final DriverLocationPublisher locationPublisher;

    /**
     * Envoie la position courante du chauffeur pour une réservation donnée.
     * La position est diffusée en direct aux clients connectés.
     *
     * @param requester    chauffeur authentifié (driverId obligation, rôle CHAUFFEUR)
     * @param reservationId identifiant de la réservation concernée
     * @param request      payload GPS validé (latitude, longitude, précision optionnelle)
     */
    @PostMapping("/{id}/location")
    public ResponseEntity<Void> publishLocation(
            @AuthenticationPrincipal AuthenticatedUser requester,
            @PathVariable UUID id,
            @Valid @RequestBody DriverLocationRequest request) {

        requireDriver(requester);

        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.vora.reservation.application.exception.ReservationNotFoundException(id));

        if (reservation.getTurn() == null) {
            throw new ReservationNotAcceptedException(
                    "COURSE_NON_ATTRIBUEE : cette réservation n'est pas affectée à un chauffeur.");
        }

        if (reservation.getStatus() != ReservationStatus.EN_COURS) {
            throw new ReservationNotStartedException(
                    "COURSE_NON_DEMARREE : la réservation n'est pas encore en cours de transport.");
        }

        // Vérification fine : le chauffeur qui publie est bien celui du Turn.
        Turn turn = reservation.getTurn();

        if (turn != null && !turn.getDriverId().equals(requester.driverId())) {
            throw new ForbiddenOperationException(
                    "Vous n'êtes pas le chauffeur affecté à cette course.");
        }

        log.info("Chauffeur {} a publié sa position pour la réservation {} (lat={}, lng={})",
                requester.driverId(), id, request.latitude(), request.longitude());

        locationPublisher.publish(
                reservation,
                request.latitude().doubleValue(),
                request.longitude().doubleValue(),
                request.precision());

        return ResponseEntity.ok().build();
    }

    private void requireDriver(AuthenticatedUser requester) {
        if (requester == null || requester.role() != com.vora.reservation.infrastructure.security.VoraRole.CHAUFFEUR
                || requester.driverId() == null) {
            throw new ForbiddenOperationException("Seul un chauffeur authentifié peut publier sa position.");
        }
    }
}
