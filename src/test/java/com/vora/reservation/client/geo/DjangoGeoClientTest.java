package com.vora.reservation.client.geo;
import com.vora.reservation.application.exception.GeoServiceUnavailableException;
import com.vora.reservation.infrastructure.client.geo.DjangoGeoClient;
import com.vora.reservation.infrastructure.client.geo.dto.GeoPoint;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationRequest;
import com.vora.reservation.infrastructure.client.geo.dto.VerifyDestinationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.http.MediaType;
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
 * {@link MockServerRestClientCustomizer} permet de brancher un
 * {@link MockRestServiceServer} sur un {@link RestClient.Builder}, sans
 * dépendance supplémentaire (disponible depuis Spring Boot 3.2 via
 * spring-boot-starter-test, déjà présent dans le pom).
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
        this.client = new DjangoGeoClient(builder.build());
    }

    private VerifyDestinationRequest sampleRequest() {
        return new VerifyDestinationRequest(
                new GeoPoint(new BigDecimal("3.866700"), new BigDecimal("11.516700")),
                new GeoPoint(new BigDecimal("3.883300"), new BigDecimal("11.516700")));
    }

    @Test
    void shouldReturnCompatibleCorridorsWhenDestinationIsValid() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/geo/verify-destination"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "valid": true,
                          "toleranceMeters": 750.0,
                          "compatibleCorridors": [
                            {"driverId": 42, "trajectoryId": "5b1f6f2e-1f0a-4b0a-9e3e-000000000001", "distanceMeters": 180.5}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        VerifyDestinationResponse response = client.verifyDestination(sampleRequest());

        assertThat(response.valid()).isTrue();
        assertThat(response.compatibleCorridors()).hasSize(1);
        assertThat(response.compatibleCorridors().get(0).driverId()).isEqualTo(42L);
        mockServer.verify();
    }

    @Test
    void shouldReturnValidWithNoCompatibleCorridorWhenNoDriverAvailable() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/geo/verify-destination"))
                .andRespond(withSuccess("""
                        {"valid": true, "toleranceMeters": 750.0, "compatibleCorridors": []}
                        """, MediaType.APPLICATION_JSON));

        VerifyDestinationResponse response = client.verifyDestination(sampleRequest());

        assertThat(response.valid()).isTrue();
        assertThat(response.compatibleCorridors()).isEmpty();
        mockServer.verify();
    }

    @Test
    void shouldThrowGeoServiceUnavailableOnServerError() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/geo/verify-destination"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.verifyDestination(sampleRequest()))
                .isInstanceOf(GeoServiceUnavailableException.class);
        mockServer.verify();
    }

    @Test
    void shouldThrowGeoServiceUnavailableOnConnectionFailure() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/geo/verify-destination"))
                .andRespond(request -> {
                    throw new IOException("connexion refusée (simulation)");
                });

        assertThatThrownBy(() -> client.verifyDestination(sampleRequest()))
                .isInstanceOf(GeoServiceUnavailableException.class);
    }
}
