package com.vora.reservation.api.dto;
import com.vora.reservation.domain.enums.OfferStatus;
import com.vora.reservation.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
public record ReservationOfferResponse(UUID id,
                                       UUID reservationId,
                                       Long driverId,
                                       OfferStatus status,
                                       OffsetDateTime sentAt,
                                       OffsetDateTime acceptedAt,
                                       OffsetDateTime expiredAt,
                                       PickupLocationDto pickup,
                                       DestinationDto destination,
                                       BigDecimal proposedPrice,
                                       PaymentMethod paymentMethod) {
}
