package com.vora.reservation.infrastructure.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration STOMP/WebSocket du microservice Réservation (Phase 7).
 *
 * <p>Point d'entrée HTTP pour les connexions STOMP : {@code /ws}. CORS
 * restreint à l'origine frontend Vercel en production (à compléter avec
 * l'URL réelle une fois connue).
 *
 * <p>Topic de diffusion sortant défini au cadrage §13 :
 * {@code /topic/reservations/{id}/driver-location}. Le préfixe
 * {@code /topic} est délivré par le broker in-memory par défaut.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Point d'entrée STOMP accessible par le frontend. */
    private static final String WS_ENDPOINT = "/ws";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        // Aucun préfixe d'envoi {@code /app} n'est requis pour le moment :
        // le chauffeur publie par REST, pas par STOMP (option A validée).
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(WS_ENDPOINT)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
