package com.vora.reservation.infrastructure.client.geo.dto;
/**
 * Corps de la requête {@code POST /api/v1/geo/verify-destination} (cadrage
 * §9.1). Ne transporte volontairement pas de {@code reservationId} : cette
 * vérification a lieu avant la persistance définitive de la réservation
 * (voir {@code ReservationService#create}), donc aucun identifiant stable
 * n'existe encore à cet instant.
 */
public record VerifyDestinationRequest(GeoPoint pickup, GeoPoint destination) {
}
