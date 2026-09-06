package com.vora.reservation.infrastructure.client.geo;

import com.vora.reservation.application.exception.GeoServiceUnavailableException;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationRequest;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationResponse;

/**
 * Client REST vers Django Geo/Optimization (cadrage §9.1 et §15). Ne
 * réimplémente jamais la vérification spatiale (PostGIS) ni l'optimisation
 * d'insertion : ce n'est qu'un adaptateur HTTP vers ce service externe.
 */
public interface GeoClient {
    /**
     * Appelle {@code POST /api/v1/geo/verify-destination} (cadrage §6,
     * étape 3).
     *
     * @throws GeoServiceUnavailableException si Django Geo est indisponible,
     *         répond trop lentement, ou renvoie une erreur technique.
     */
    VerifyDestinationResponse verifyDestination(VerifyDestinationRequest request);
}
