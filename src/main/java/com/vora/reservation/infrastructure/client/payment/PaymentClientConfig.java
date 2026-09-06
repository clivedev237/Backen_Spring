package com.vora.reservation.infrastructure.client.payment;

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
 * Fournit le {@link RestClient} dédié à Node Auth & Payment : URL de base
 * ({@code vora.auth-payment.base-url}), timeouts courts (cadrage §9.2), et un
 * {@link ObjectMapper} DÉDIÉ en snake_case ({@code payment_links},
 * {@code client_id}, {@code moyen_paiement}…) pour matcher le contrat réel de
 * l'API Node (voir son OpenAPI officiel / dictionnaire payment_links).
 *
 * <p>Ce mapper est scopé à ce seul bean : il n'affecte ni l'API exposée par
 * ce microservice (camelCase, DTOs {@code api.dto}), ni un futur client Django
 * Geo qui possède sa propre configuration.
 */
@Configuration
public class PaymentClientConfig {

    @Bean
    public RestClient paymentRestClient(RestClient.Builder builder,
                                        @Value("${vora.auth-payment.base-url}") String baseUrl,
                                        @Value("${vora.geo.connect-timeout-ms:2000}") long connectTimeoutMs,
                                        @Value("${vora.geo.read-timeout-ms:3000}") long readTimeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        ObjectMapper paymentObjectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        paymentObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MappingJackson2HttpMessageConverter paymentJsonConverter =
                new MappingJackson2HttpMessageConverter(paymentObjectMapper);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(0, paymentJsonConverter);
                })
                .build();
    }
}
