package com.vora.reservation.client.geo;
import com.fasterxml.jackson.databind.JsonNode;
import com.vora.reservation.application.exception.GeoServiceUnavailableException;
import com.vora.reservation.infrastructure.client.geo.DjangoGeoClient;
import com.vora.reservation.infrastructure.client.geo.dto.LatLng;
import com.vora.reservation.infrastructure.client.geo.dto.OptimizeTurnRequest;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Utilise le même {@link MappingJackson2HttpMessageConverter} snake_case
 * que {@code GeoClientConfig} pour vérifier que le JSON envoyé à Django
 * Geo respecte bien son contrat réel ({@code driver_id}, {@code tolerance_meters}...).
 */
class DjangoGeoClientTest {
    private static final String BASE_URL = "http://geo.test";

    private MockRestServiceServer mockServer;
    private DjangoGeoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockServerRestClientCustomizer customizer = new MockServerRestClientCustomizer();
        customizer.customize(builder);
        this.mockServer = customizer.getServer();

        com.fasterxml.jackson.databind.ObjectMapper snakeCaseMapper =
                com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                        .build();
        builder.messageConverters(converters -> {
            converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
            converters.add(0, new MappingJackson2HttpMessageConverter(snakeCaseMapper));
        });

        this.client = new DjangoGeoClient(builder.build());
    }

    @Test
    void shouldSendSnakeCaseBodyForVerifyDestination() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/geo/verify-destination"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"driver_id": 42, "destination": {"lat": 3.8833, "lng": 11.5167}}
                        """))
                .andRespond(withSuccess("""
                        {"compatible": true}
                        """, MediaType.APPLICATION_JSON));

        VerifyDestinationRequest request = new VerifyDestinationRequest(
                42L, new LatLng(new BigDecimal("3.8833"), new BigDecimal("11.5167")));

        JsonNode response = client.verifyDestination(request);

        assertThat(response.get("compatible").asBoolean()).isTrue();
        mockServer.verify();
    }

    @Test
    void shouldSendSnakeCaseBodyForOptimizeTurn() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/optimize/turn"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"driver_id": 42}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        JsonNode response = client.optimizeTurn(new OptimizeTurnRequest(42L));

        assertThat(response).isNotNull();
        mockServer.verify();
    }

    @Test
    void shouldThrowGeoServiceUnavailableOnServerError() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/geo/verify-destination"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.verifyDestination(
                new VerifyDestinationRequest(42L, new LatLng(BigDecimal.ZERO, BigDecimal.ZERO))))
                .isInstanceOf(GeoServiceUnavailableException.class);
        mockServer.verify();
    }

    @Test
    void shouldThrowGeoServiceUnavailableOnConnectionFailure() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/optimize/turn"))
                .andRespond(request -> {
                    throw new IOException("connexion refusée (simulation)");
                });

        assertThatThrownBy(() -> client.optimizeTurn(new OptimizeTurnRequest(42L)))
                .isInstanceOf(GeoServiceUnavailableException.class);
    }
}
