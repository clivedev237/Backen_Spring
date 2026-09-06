package com.vora.reservation.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PickupLocationDto(@NotNull(message = "La latitude de départ est obligatoire.")
                                 @DecimalMin(value = "-90.0", message = "La latitude doit être comprise entre -90 et 90.")
                                 @DecimalMax(value = "90.0", message = "La latitude doit être comprise entre -90 et 90.")
                                BigDecimal latitude,

                                @NotNull(message = "La longitude de départ est obligatoire.")
                                 @DecimalMin(value = "-180.0", message = "La longitude doit être comprise entre -180 et 180.")
                                 @DecimalMax(value = "180.0", message = "La longitude doit être comprise entre -180 et 180.")
                                 BigDecimal longitude,

                                @Size(max = 255, message = "La précision de départ ne doit pas dépasser 255 caractères.")
                                 String precision) {
}
