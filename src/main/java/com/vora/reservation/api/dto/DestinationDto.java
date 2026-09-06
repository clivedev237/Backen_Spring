package com.vora.reservation.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DestinationDto(
        @NotBlank(message = "L'adresse de destination est obligatoire.")
        @Size(max = 255, message = "L'adresse de destination ne doit pas dépasser 255 caractères.")
        String address,

        @NotNull(message = "La latitude de destination est obligatoire.")
        @DecimalMin(value = "-90.0", message = "La latitude doit être comprise entre -90 et 90.")
        @DecimalMax(value = "90.0", message = "La latitude doit être comprise entre -90 et 90.")
        BigDecimal latitude,

        @NotNull(message = "La longitude de destination est obligatoire.")
        @DecimalMin(value = "-180.0", message = "La longitude doit être comprise entre -180 et 180.")
        @DecimalMax(value = "180.0", message = "La longitude doit être comprise entre -180 et 180.")
        BigDecimal longitude) {
}
