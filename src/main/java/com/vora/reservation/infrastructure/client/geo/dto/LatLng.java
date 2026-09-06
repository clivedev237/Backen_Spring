package com.vora.reservation.infrastructure.client.geo.dto;

import java.math.BigDecimal;

/**
 * Point géographique tel qu'attendu par Django Geo (schéma {@code LatLng}
 * de l'OpenAPI officiel : champs {@code lat}/{@code lng}). Remplace
 * {@code GeoPoint} (latitude/longitude), introduit par hypothèse en
 * Phase 3 : le contrat réel utilise ces noms courts.
 */
public record LatLng(BigDecimal lat, BigDecimal lng) {
}
