package com.vora.reservation.infrastructure.notification;

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
 * Fournit le {@link RestClient} dédié au webhook de notification frontend.
 *
 * <p>Timeouts courts (Phase 9, §9.2 : appels synchrones courts). Le webhook
 * front est appelé de façon synchrone mais non-bloquant : si le frontend est
 * indisponible, la notification est perdue sans impacter le workflow métier.
 */
@Configuration
public class NotificationClientConfig {

    @Bean
    public RestClient notificationRestClient(RestClient.Builder builder,
                                             @Value("${vora.notification.connect-timeout-ms:2000}") long connectTimeoutMs,
                                             @Value("${vora.notification.read-timeout-ms:3000}") long readTimeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
