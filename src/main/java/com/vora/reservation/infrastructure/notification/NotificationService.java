package com.vora.reservation.infrastructure.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vora.reservation.application.exception.NotificationUnavailableException;
import com.vora.reservation.infrastructure.notification.dto.NotificationEvent;
import com.vora.reservation.infrastructure.notification.dto.NotificationEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Service de notification vers le frontend via webhook HTTP POST (Phase 9).
 *
 * <p>Canal : webhook front (React PWA, Vercel). Le frontend expose un endpoint
 * {@code POST /api/v1/notifications} (ou équivalent) qui reçoit les événements
 * métier de ce microservice.
 *
 * <p>Stratégie de résilience :
 * - timeouts courts (définis dans application.yml, par défaut 2s/3s) ;
 * - retries limités sur échec réseau (max-retries, backoff linéaire simple) ;
 * - indisponibilité du frontend NE bloque pas les traitements métier : on logue
 *   l'échec et on continue (la notification est "best effort").
 * - idempotence côté frontend attendue sur {@code eventId}.
 *
 * <p>À confirmer avec l'équipe frontend :
 * - URL exacte de l'endpoint webhook (qui reçoit les notifications) ;
 * - Headers additionnels requis (ex. auth, origine) ;
 * - Contenu exact du {@code data} pour chaque type d'événement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RestClient notificationRestClient;
    private final ObjectMapper objectMapper;

    @Value("${vora.notification.webhook-url}")
    private String webhookUrl;

    @Value("${vora.notification.connect-timeout-ms:2000}")
    private long connectTimeoutMs;

    @Value("${vora.notification.read-timeout-ms:3000}")
    private long readTimeoutMs;

    @Value("${vora.notification.max-retries:3}")
    private int maxRetries;

    /**
     * Envoie un événement de notification au frontend.
     *
     * <p>Best-effort : si le frontend est indisponible après tous les retries,
     * l'événement est perdu silencieusement (logué).
     *
     * @param event événement à envoyer
     */
    public void send(NotificationEvent event) {
        if (webhookUrl == null || webhookUrl.isBlank() || "null".equals(webhookUrl)) {
            log.debug("Webhook notification désactivé (url vide) — événement ignoré : {}", event.getType());
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            sendWithRetry(payload);
        } catch (JsonProcessingException e) {
            log.error("Erreur sérialisation événement notification {} : {}", event.getType(), e.getMessage());
        }
    }

    /**
     * Envoie un événement avec retry linéaire simple.
     *
     * @param payload JSON sérialisé de l'événement
     */
    private void sendWithRetry(String payload) {
        int attempt = 0;
        Throwable lastFailure = null;

        while (attempt <= maxRetries) {
            try {
                notificationRestClient.post()
                        .uri(webhookUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Notification-Event-Id", extractEventIdFromPayload(payload))
                        .header("X-Notification-Type", extractTypeFromPayload(payload))
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                return; // succès
            } catch (Exception e) {
                lastFailure = e;
                attempt++;
                if (attempt <= maxRetries) {
                    long backoffMs = attempt * 500L; // backoff linéaire 0.5s, 1s, 1.5s
                    log.warn("Échec envoi notification (attempt {}/{}) : {} — retry dans {} ms",
                            attempt, maxRetries, e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Retry notification interrompu");
                        return;
                    }
                }
            }
        }

        log.error("Notification webhook non délivrée après {} tentatives : {}",
                maxRetries + 1, lastFailure != null ? lastFailure.getMessage() : "erreur inconnue");
    }

    /**
     * Convenience : construit et envoie un événement {@link NotificationEventType#RESERVATION_ASSIGNED}
     * avec les données minimales (chauffeur, turn).
     */
    public void notifyReservationAssigned(UUID reservationId, Long driverId, UUID turnId) {
        send(NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .type(NotificationEventType.RESERVATION_ASSIGNED)
                .reservationId(reservationId)
                .turnId(turnId)
                .driverId(driverId)
                .timestamp(System.currentTimeMillis())
                .data(Map.of("driverId", driverId, "turnId", turnId != null ? turnId.toString() : null))
                .build());
    }

    /**
     * Convenience : construit et envoie un événement {@link NotificationEventType#DRIVER_STARTED}.
     */
    public void notifyDriverStarted(UUID reservationId, UUID turnId) {
        send(NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .type(NotificationEventType.DRIVER_STARTED)
                .reservationId(reservationId)
                .turnId(turnId)
                .timestamp(System.currentTimeMillis())
                .data(Map.of("turnId", turnId != null ? turnId.toString() : null))
                .build());
    }

    /**
     * Convenience : construit et envoie un événement {@link NotificationEventType#PAYMENT_INITIATED}.
     */
    public void notifyPaymentInitiated(UUID reservationId) {
        send(NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .type(NotificationEventType.PAYMENT_INITIATED)
                .reservationId(reservationId)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * Convenience : construit et envoie un événement {@link NotificationEventType#PAYMENT_SUCCEEDED}.
     */
    public void notifyPaymentSucceeded(UUID reservationId) {
        send(NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .type(NotificationEventType.PAYMENT_SUCCEEDED)
                .reservationId(reservationId)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * Convenience : construit et envoie un événement {@link NotificationEventType#ARRIVAL_CONFIRMED}.
     */
    public void notifyArrivalConfirmed(UUID reservationId) {
        send(NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .type(NotificationEventType.ARRIVAL_CONFIRMED)
                .reservationId(reservationId)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    // -------------------------------------------------------------------------
    // Helpers privés
    // -------------------------------------------------------------------------

    private String extractEventIdFromPayload(String payload) {
        try {
            var node = objectMapper.readTree(payload);
            if (node.has("eventId") && !node.get("eventId").isNull()) {
                return node.get("eventId").asText();
            }
        } catch (JsonProcessingException ignored) {
        }
        return "";
    }

    private String extractTypeFromPayload(String payload) {
        try {
            var node = objectMapper.readTree(payload);
            if (node.has("type") && !node.get("type").isNull()) {
                return node.get("type").asText();
            }
        } catch (JsonProcessingException ignored) {
        }
        return "";
    }
}
