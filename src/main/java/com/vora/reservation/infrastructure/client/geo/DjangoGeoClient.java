package com.vora.reservation.infrastructure.client.geo;


import com.fasterxml.jackson.databind.JsonNode;
import com.vora.reservation.application.exception.GeoServiceUnavailableException;
import com.vora.reservation.infrastructure.client.geo.dto.OptimizeTurnRequest;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptateur REST synchrone vers Django Geo (cadrage §10 : {@link RestClient},
 * pas besoin de spring-boot-starter-webflux).
 */
@Component
@Slf4j
public class DjangoGeoClient implements GeoClient {
    private static final String VERIFY_DESTINATION_PATH = "/api/v1/geo/verify-destination";
    private static final String OPTIMIZE_TURN_PATH = "/api/v1/optimize/turn";

    private final RestClient geoRestClient;

    public DjangoGeoClient(@Qualifier("geoRestClient") RestClient geoRestClient) {
        this.geoRestClient = geoRestClient;
    }

    @Override
    public JsonNode verifyDestination(VerifyDestinationRequest request) {
        return post(VERIFY_DESTINATION_PATH, request);
    }

    @Override
    public JsonNode optimizeTurn(OptimizeTurnRequest request) {
        return post(OPTIMIZE_TURN_PATH, request);
    }

    private JsonNode post(String path, Object body) {
        try {
            return geoRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            log.warn("Appel à Django Geo ({}) en échec : {}", path, ex.getMessage());
            throw new GeoServiceUnavailableException(
                    "Django Geo est indisponible ou a répondu trop lentement (" + path + ").", ex);
        }
    }
}
