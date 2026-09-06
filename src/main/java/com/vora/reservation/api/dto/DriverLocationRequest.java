package com.vora.reservation.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload d'envoi de position GPS par le chauffeur (Phase 7, option A : REST).
 * Le {@code reservationId} est le seul identifiant métier autorisé dans le
 * corps ; l'identité du chauffeur (driverId) vient exclusivement des headers
 * Gateway. La précision est informative (ex. "Navigo 3m", "GPS galère").
 */
public record DriverLocationRequest(
        @NotNull(message = "L'identifiant de réservation est obligatoire.")
        java.util.UUID reservationId,

        @NotNull(message = "La latitude est obligatoire.")
        @DecimalMin(value = "-90.0", message = "La latitude doit être comprise entre -90 et 90.")
        @DecimalMax(value = "90.0", message = "La latitude doit être comprise entre -90 et 90.")
        BigDecimal latitude,

        @NotNull(message = "La longitude est obligatoire.")
        @DecimalMin(value = "-180.0", message = "La longitude doit être comprise entre -180 et 180.")
        @DecimalMax(value = "180.0", message = "La longitude doit être comprise entre -180 et 180.")
        BigDecimal longitude,

        @Size(max = 255, message = "La précision ne doit pas dépasser 255 caractères.")
        String precision
) {
}
