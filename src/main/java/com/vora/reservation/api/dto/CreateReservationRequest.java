package com.vora.reservation.api.dto;

import com.vora.reservation.domain.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
public record CreateReservationRequest(
        @NotNull(message = "Le point de départ est obligatoire.")
        @Valid
        PickupLocationDto pickup,

        @NotNull(message = "La destination est obligatoire.")
        @Valid
        DestinationDto destination,

        @NotNull(message = "Le prix proposé est obligatoire.")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le prix proposé doit être strictement positif.")
        @Digits(integer = 8, fraction = 2, message = "Le prix proposé doit avoir au plus 8 chiffres et 2 décimales.")
        BigDecimal proposedPrice,

        @NotNull(message = "Le mode de paiement est obligatoire.")
        PaymentMethod paymentMethod
) {
}
