package com.vora.reservation.infrastructure.client.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.vora.reservation.application.exception.GeoServiceUnavailableException;
import com.vora.reservation.infrastructure.client.geo.dto.OptimizeTurnRequest;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationRequest;

/**
 * Client REST vers Django Geo/Optimization (cadrage §9.1 et §15). Ne
 * réimplémente jamais la vérification spatiale (PostGIS) ni l'optimisation
 * d'insertion : ce n'est qu'un adaptateur HTTP vers ce service externe.
 *
 * <p><b>Réponses non typées (temporaire)</b> : le contrat de requête est
 * confirmé (OpenAPI officiel de Django Geo), mais le contrat de réponse ne
 * l'est pas (schéma OpenAPI générique côté Django, sans champs déclarés).
 * Les méthodes renvoient donc un {@link JsonNode} brut plutôt qu'un DTO
 * inventé une nouvelle fois par hypothèse ; elles seront typées dès qu'un
 * exemple réel ou le code des vues Django sera disponible.
 */
public interface GeoClient {
    /**
     * Appelle {@code POST /api/v1/geo/verify-destination} pour UN chauffeur
     * donné (cadrage §6, étape 3).
     *
     * @throws GeoServiceUnavailableException si Django Geo est indisponible,
     *         répond trop lentement, ou renvoie une erreur technique.
     */
    JsonNode verifyDestination(VerifyDestinationRequest request);

    /**
     * Appelle {@code POST /api/v1/optimize/turn} pour un chauffeur donné
     * (cadrage §6, étape 4).
     *
     * @throws GeoServiceUnavailableException si Django Geo est indisponible,
     *         répond trop lentement, ou renvoie une erreur technique.
     */
    JsonNode optimizeTurn(OptimizeTurnRequest request);
}
