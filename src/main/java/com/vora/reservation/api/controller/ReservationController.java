package com.vora.reservation.api.controller;


import com.vora.reservation.api.dto.CreateReservationRequest;
import com.vora.reservation.api.dto.ReservationResponse;
import com.vora.reservation.api.mapper.ReservationMapper;
import com.vora.reservation.application.service.ReservationService;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    /**
     * Création d'une réservation par un client (cadrage §13.1). Le client
     * s'identifie via les headers Gateway ; il ne peut pas créer une
     * réservation au nom d'un autre client.
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> create(@AuthenticationPrincipal AuthenticatedUser requester,
                                                      @Valid @RequestBody CreateReservationRequest request) {
        Reservation reservation = reservationService.create(requester, request);
        URI location = URI.create("/api/v1/reservations/" + reservation.getId());
        return ResponseEntity.created(location).body(ReservationMapper.toResponse(reservation));
    }

    /**
     * Consultation d'une réservation par identifiant. Un client ne peut voir
     * que ses propres réservations ; un admin voit tout.
     */
    @GetMapping("/{id}")
    public ReservationResponse getById(@AuthenticationPrincipal AuthenticatedUser requester,
                                       @PathVariable UUID id) {
        return ReservationMapper.toResponse(reservationService.getById(requester, id));
    }

    /**
     * Liste paginée des réservations. Pour un CLIENT : uniquement les
     * siennes. Pour un ADMIN : toutes, avec filtre optionnel par client.
     * Filtre optionnel par statut dans les deux cas.
     */
    @GetMapping
    public Page<ReservationResponse> list(@AuthenticationPrincipal AuthenticatedUser requester,
                                          @RequestParam(required = false) ReservationStatus status,
                                          @RequestParam(required = false) Long clientId,
                                          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                          Pageable pageable) {
        return reservationService.list(requester, status, clientId, pageable)
                .map(ReservationMapper::toResponse);
    }
}
