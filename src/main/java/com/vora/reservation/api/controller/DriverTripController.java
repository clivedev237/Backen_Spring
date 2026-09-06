package com.vora.reservation.api.controller;

import com.vora.reservation.api.dto.ReservationResponse;
import com.vora.reservation.api.mapper.ReservationMapper;
import com.vora.reservation.application.service.DriveTripService;
import com.vora.reservation.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints côté chauffeur pour le suivi du voyage (démarrage de la course).
 * Seul le chauffeur affecté peut appeler ces routes.
 */
@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverTripController {
    private final DriveTripService driveTripService;

    @PostMapping("/reservations/{id}/start")
    public ResponseEntity<ReservationResponse> startTrip(
            @AuthenticationPrincipal AuthenticatedUser requester,
            @PathVariable UUID id) {
        ReservationResponse response = ReservationMapper.toResponse(
                driveTripService.startTrip(requester, id));
        return ResponseEntity.ok(response);
    }
}
