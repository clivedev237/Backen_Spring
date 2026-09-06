package com.vora.reservation.application.service;

import com.vora.reservation.api.dto.CreateReservationRequest;
import com.vora.reservation.application.exception.ForbiddenOperationException;
import com.vora.reservation.application.exception.ReservationNotFoundException;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.infrastructure.persistence.ReservationRepository;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import com.vora.reservation.infrastructure.security.VoraRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository reservationRepository;

    /**
     * Crée une réservation pour le client authentifié. Seul un CLIENT peut
     * créer une réservation ; le {@code clientId} provient exclusivement de
     * l'identité posée par le Gateway, jamais du corps de la requête.
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

    /**
     * Consultation d'une réservation par identifiant. Un client ne peut voir
     * que ses propres réservations ; un admin voit tout. L'accès chauffeur
     * n'est pas encore couvert : il arrivera avec la diffusion des offres
     * (Phase 5), une fois qu'un chauffeur peut légitimement être concerné par
     * une réservation qui ne lui appartient pas.
     */
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

    /**
     * Liste paginée des réservations. Un CLIENT ne voit que les siennes (le
     * filtre {@code clientId} demandé, s'il en fournit un, est ignoré). Un
     * ADMIN peut filtrer par client. Un CHAUFFEUR n'a pas encore accès à
     * cette consultation (voir {@link #getById}).
     */
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
