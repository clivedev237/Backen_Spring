package com.vora.reservation.infrastructure.client.geo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fournit le {@link RestClient} dédié à Django Geo : URL de base
 * ({@code vora.geo.base-url}), timeouts courts (cadrage §9.2), et un
 * {@link ObjectMapper} DÉDIÉ en snake_case ({@code driver_id},
 * {@code tolerance_meters}, {@code zone_id}...) pour matcher le contrat
 * réel de l'API Django (voir son OpenAPI officiel). Ce mapper est scopé à
 * ce seul bean : il n'affecte ni l'API exposée par ce microservice
 * (camelCase, nos DTOs {@code api.dto}), ni un futur client Node Auth &
 * Payment qui pourrait avoir une autre convention.
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

        ObjectMapper geoObjectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        geoObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MappingJackson2HttpMessageConverter geoJsonConverter =
                new MappingJackson2HttpMessageConverter(geoObjectMapper);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(0, geoJsonConverter);
                })
                .build();
    }
}
