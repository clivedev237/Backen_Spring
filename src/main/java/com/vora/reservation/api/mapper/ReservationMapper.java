package com.vora.reservation.api.mapper;

import com.vora.reservation.api.dto.DestinationDto;
import com.vora.reservation.api.dto.InitiatePaymentResponseDto;
import com.vora.reservation.api.dto.PaymentReferenceDto;
import com.vora.reservation.api.dto.PaymentStatusResponseDto;
import com.vora.reservation.api.dto.PickupLocationDto;
import com.vora.reservation.api.dto.ReservationResponse;
import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.PaymentRefStatus;
import com.vora.reservation.domain.enums.ReservationStatus;
import com.vora.reservation.domain.model.PaymentReference;
import com.vora.reservation.domain.model.Reservation;
import com.vora.reservation.infrastructure.client.payment.dto.InitiatePaymentResponse;
import com.vora.reservation.infrastructure.client.payment.dto.PaymentStatusResponse;

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
                toPaymentReferenceDto(reservation.getPaymentReference()),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }

    public static InitiatePaymentResponseDto toInitiatePaymentResponseDto(InitiatePaymentResponse response) {
        if (response == null) {
            return null;
        }
        return new InitiatePaymentResponseDto(
                response.getId(),
                response.getToken(),
                response.getStatus(),
                response.getCreatedAt(),
                response.getAmount(),
                response.getPaymentMethod()
        );
    }

    public static PaymentStatusResponseDto toPaymentStatusResponseDto(PaymentStatusResponse response) {
        if (response == null) {
            return null;
        }
        return new PaymentStatusResponseDto(
                response.getId(),
                response.getToken(),
                response.getStatus(),
                response.getAmount(),
                response.getPaymentMethod(),
                response.getUpdatedAt()
        );
    }

    private static PaymentReferenceDto toPaymentReferenceDto(PaymentReference ref) {
        if (ref == null) {
            return null;
        }
        return new PaymentReferenceDto(
                ref.getId(),
                ref.getPaymentId(),
                ref.getAmount(),
                ref.getMethod(),
                ref.getStatus(),
                ref.getUpdatedAt()
        );
    }
}

