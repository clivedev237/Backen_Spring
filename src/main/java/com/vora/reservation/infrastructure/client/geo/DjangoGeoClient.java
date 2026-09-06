package com.vora.reservation.infrastructure.client.geo;

import com.vora.reservation.application.exception.GeoServiceUnavailableException;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationRequest;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptateur REST synchrone vers Django Geo (cadrage §10 : Spring WebClient
 * ou RestTemplate — on retient ici {@link RestClient}, disponible depuis
 * Spring Framework 6.1 / Spring Boot 3.2 sans dépendance supplémentaire
 * (pas besoin de spring-boot-starter-webflux), ce qui évite d'introduire
 * une dépendance Maven non vérifiable dans le sandbox de développement).
 */
@Component
@Slf4j
public class DjangoGeoClient implements GeoClient {
    private static final String VERIFY_DESTINATION_PATH = "/api/v1/geo/verify-destination";

    private final RestClient geoRestClient;

    public DjangoGeoClient(@Qualifier("geoRestClient") RestClient geoRestClient) {
        this.geoRestClient = geoRestClient;
    }

    @Override
    public VerifyDestinationResponse verifyDestination(VerifyDestinationRequest request) {
        try {
            return geoRestClient.post()
                    .uri(VERIFY_DESTINATION_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(VerifyDestinationResponse.class);
        } catch (RestClientException ex) {
            // Timeout, connexion refusée, ou statut HTTP d'erreur renvoyé par Django Geo :
            // traité ici comme une indisponibilité technique (cadrage §16). La stratégie de
            // repli "ne pas bloquer la réservation" (retry/circuit breaker, cadrage §9.2) est
            // explicitement déférée à la Phase 10 (résilience) — pour l'instant on échoue
            // proprement avec un message clair plutôt que de dégrader silencieusement.
            log.warn("Appel à Django Geo ({}) en échec : {}", VERIFY_DESTINATION_PATH, ex.getMessage());
            throw new GeoServiceUnavailableException(
                    "Le service de vérification spatiale (Django Geo) est indisponible ou a répondu trop lentement.",
                    ex);
        }
    }
}
