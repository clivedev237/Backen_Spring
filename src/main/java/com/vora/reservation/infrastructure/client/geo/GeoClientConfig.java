package com.vora.reservation.infrastructure.client.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fournit le {@link RestClient} dédié à Django Geo, avec l'URL de base
 * ({@code vora.geo.base-url}, déjà configurée en Phase 1) et des timeouts
 * courts (cadrage §9.2 : "prévoir des timeouts courts... pour ne pas
 * bloquer une réservation"). Bean qualifié par son nom ("geoRestClient")
 * pour ne pas entrer en conflit avec le futur RestClient de la Phase 8
 * (Node Auth & Payment).
 */
@Configuration
public class GeoClientConfig {

    @Bean
    public RestClient geoRestClient(RestClient.Builder builder,
                                    @Value("${vora.geo.base-url}") String baseUrl,
                                    @Value("${vora.geo.connect-timeout-ms:2000}") long connectTimeoutMs,
                                    @Value("${vora.geo.read-timeout-ms:3000}") long readTimeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
