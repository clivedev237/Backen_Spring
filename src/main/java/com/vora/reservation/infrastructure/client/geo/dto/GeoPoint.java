package com.vora.reservation.infrastructure.client.geo.dto;
import java.math.BigDecimal;
/**
 * Point géographique (latitude/longitude) utilisé dans les échanges avec
 * Django Geo. Distinct des DTOs {@code api.dto} pour ne pas coupler le
 * contrat HTTP client/serveur au contrat de l'API exposée par ce
 * microservice (cadrage §15 : infrastructure/client-geo).
 */

public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
}
