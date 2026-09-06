package com.vora.reservation.api.mapper;

import com.vora.reservation.api.dto.DestinationDto;
import com.vora.reservation.api.dto.PickupLocationDto;
import com.vora.reservation.api.dto.ReservationResponse;
import com.vora.reservation.domain.model.Reservation;

public class ReservationMapper {
    private ReservationMapper() {
    }

    public static ReservationResponse toResponse(Reservation reservation) {
        PickupLocationDto pickup = new PickupLocationDto(
                reservation.getPickupLatitude(),
                reservation.getPickupLongitude(),
                reservation.getPickupPrecision()
        );

        DestinationDto destination = new DestinationDto(
                reservation.getDestinationAddress(),
                reservation.getDestinationLatitude(),
                reservation.getDestinationLongitude()
        );

        return new ReservationResponse(
                reservation.getId(),
                reservation.getClientId(),
                reservation.getTurn() != null ? reservation.getTurn().getId() : null,
                reservation.getSequenceIndex(),
                pickup,
                destination,
                reservation.getProposedPrice(),
                reservation.getPaymentMethod(),
                reservation.getStatus(),
                reservation.getBoardedAt(),
                reservation.getArrivedAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}
