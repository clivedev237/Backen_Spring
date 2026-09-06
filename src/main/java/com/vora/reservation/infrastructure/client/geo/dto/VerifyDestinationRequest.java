package com.vora.reservation.infrastructure.client.geo.dto;

/**
 * Corps de la requête {@code POST /api/v1/geo/verify-destination}, conforme
 * au schéma {@code VerifyDestination} de l'OpenAPI officiel de Django Geo
 * (obtenu du camarade Django, remplace l'hypothèse de Phase 3).
 *
 * <p><b>Correction par rapport à la Phase 3</b> : cet endpoint vérifie la
 * destination pour UN SEUL chauffeur ({@code driverId}, obligatoire) — ce
 * n'est pas une recherche multi-chauffeurs, et il n'y a pas de champ
 * {@code pickup}. {@code toleranceMeters} reste optionnel (géré par
 * Django, cadrage §5/§20) : Réservation ne le renseigne pas par défaut.
 *
 * <p><b>Point bloquant non résolu</b> : la manière dont Réservation obtient
 * le(s) {@code driverId} candidat(s) à tester n'est pas encore définie
 * (pas d'endpoint de découverte "chauffeurs actifs à proximité" dans le
 * contrat fourni) — à confirmer avant de reconnecter cet appel dans
 * {@code ReservationService}.
 */
public record VerifyDestinationRequest(Long driverId, LatLng destination, Integer toleranceMeters) {
    public VerifyDestinationRequest(Long driverId, LatLng destination) {
        this(driverId, destination, null);
    }
}
