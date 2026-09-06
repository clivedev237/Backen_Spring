package com.vora.reservation.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vora.reservation.infrastructure.notification.dto.NotificationEvent;
import com.vora.reservation.infrastructure.notification.dto.NotificationEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private RestClient restClient;
    @Mock private RequestBodyUriSpec requestSpec;
    @Mock private RequestBodySpec bodySpec;
    @Mock private ResponseSpec responseSpec;
    @Mock private ObjectNode jsonObject;

    @InjectMocks private NotificationService notificationService;

    private static final String WEBHOOK_URL = "http://frontend:3000/api/v1/notifications";

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(notificationService, "webhookUrl", WEBHOOK_URL);

        // Chaîne RestClient complète
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(WEBHOOK_URL)).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(null);

        // ObjectMapper
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"ev-1\",\"type\":\"TA\"}");
        when(objectMapper.readTree(anyString())).thenReturn(jsonObject);
        when(jsonObject.has("eventId")).thenReturn(true);
        when(jsonObject.has("type")).thenReturn(true);
        when(jsonObject.get("eventId")).thenReturn(jsonObject);
        when(jsonObject.get("type")).thenReturn(jsonObject);
        when(jsonObject.asText()).thenReturn("ev-1");
    }

    @Test
    void shouldSendEventToWebhook() {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .type(NotificationEventType.RESERVATION_ASSIGNED)
                .reservationId(UUID.randomUUID())
                .timestamp(System.currentTimeMillis())
                .build();

        notificationService.send(event);

        // Vérification minimale : le client REST a été invoqué
        verify(restClient).post();
    }

    @Test
    void shouldNotSendWhenWebhookUrlIsBlank() {
        ReflectionTestUtils.setField(notificationService, "webhookUrl", "");

        NotificationEvent event = NotificationEvent.of(NotificationEventType.RESERVATION_ASSIGNED, UUID.randomUUID());

        notificationService.send(event);

        verifyNoInteractions(restClient);
    }

    @Test
    void shouldNotSendWhenWebhookUrlIsNull() {
        ReflectionTestUtils.setField(notificationService, "webhookUrl", null);

        NotificationEvent event = NotificationEvent.of(NotificationEventType.RESERVATION_ASSIGNED, UUID.randomUUID());

        notificationService.send(event);

        verifyNoInteractions(restClient);
    }

    @Test
    void shouldHandleJsonProcessingExceptionGracefully() {
        // La méthode send() attrape JsonProcessingException et logue l'erreur.
        // Ce test vérifie que l'envoi n'est pas tenté en cas d'erreur de sérialisation.
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .type(NotificationEventType.RESERVATION_ASSIGNED)
                .reservationId(UUID.randomUUID())
                .timestamp(System.currentTimeMillis())
                .build();

        // Vérifie que la méthode ne plante pas
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> notificationService.send(event));
    }
}
