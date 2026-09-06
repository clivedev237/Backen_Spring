package com.vora.reservation.api.dto;

import com.vora.reservation.domain.enums.PaymentMethod;
import com.vora.reservation.domain.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationResponse( UUID id,
                                   Long clientId,
                                   UUID turnId,
                                   Integer sequenceIndex,
                                   PickupLocationDto pickup,
                                   DestinationDto destination,
                                   BigDecimal proposedPrice,
                                   PaymentMethod paymentMethod,
                                   ReservationStatus status,
                                   OffsetDateTime boardedAt,
                                   OffsetDateTime arrivedAt,
                                   OffsetDateTime createdAt,
                                   OffsetDateTime updatedAt) {
}
