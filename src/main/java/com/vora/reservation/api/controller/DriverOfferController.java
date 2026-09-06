package com.vora.reservation.api.controller;
import com.vora.reservation.api.dto.ReservationOfferResponse;
import com.vora.reservation.api.dto.ReservationResponse;
import com.vora.reservation.api.mapper.ReservationMapper;
import com.vora.reservation.api.mapper.ReservationOfferMapper;
import com.vora.reservation.application.service.OfferService;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints côté chauffeur pour les offres de réservation (cadrage §13,
 * Phase 5). Le {@code driverId} vient exclusivement de l'identité posée par
 * le Gateway ({@code X-Driver-Id}), jamais du corps ou de l'URL.
 */
@RestController
@RequestMapping("/api/v1/driver/reservation-offers")
@RequiredArgsConstructor
public class DriverOfferController {
    private final OfferService offerService;

    /**
     * Offres en attente pour le chauffeur authentifié. La compatibilité
     * spatiale a déjà été décidée à la diffusion (seuls les chauffeurs
     * candidats retenus par Django Geo reçoivent une offre) : pas de filtre
     * supplémentaire ici.
     */
    @GetMapping
    public List<ReservationOfferResponse> listPending(@AuthenticationPrincipal AuthenticatedUser requester) {
        return offerService.listPendingOffersForDriver(requester).stream()
                .map(ReservationOfferMapper::toResponse)
                .toList();
    }

    /**
     * Acceptation atomique (cadrage §8.1) : premier gagnant, verrou
     * pessimiste sur la réservation puis le Turn. Voir {@code OfferService}.
     */
    @PostMapping("/{id}/accept")
    public ReservationResponse accept(@AuthenticationPrincipal AuthenticatedUser requester,
                                      @PathVariable UUID id) {
        return ReservationMapper.toResponse(offerService.acceptOffer(requester, id));
    }

    /**
     * Refus explicite (ajout Phase 5, endpoint non documenté dans le cadrage
     * v2.1 — décision de cadrage : inclus).
     */
    @PostMapping("/{id}/decline")
    public ResponseEntity<Void> decline(@AuthenticationPrincipal AuthenticatedUser requester,
                                        @PathVariable UUID id) {
        offerService.declineOffer(requester, id);
        return ResponseEntity.noContent().build();
    }
}
