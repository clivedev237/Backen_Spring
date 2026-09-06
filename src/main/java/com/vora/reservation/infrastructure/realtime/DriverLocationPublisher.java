package com.vora.reservation.infrastructure.realtime;

import com.vora.reservation.domain.model.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Publication STOMP de la position du chauffeur vers le topic sortant défini
 * au cadrage §13 : {@code /topic/reservations/{id}/driver-location}.
 *
 * <p>Ce service est le seul point d'entrée temps réel de diffusion sortante
 * pour la position du chauffeur. Il est invoqué par le contrôleur REST driver
 * (option A validée : le chauffeur publie par {@code POST
 * /api/v1/driver/reservations/{id}/location}).
 *
 * <p>Le payload transmis est volontairement léger (lat/lng/precision) : le
 * frontend consomme directement le message STOMP sans passer par une
 * requête HTTP supplémentaire.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverLocationPublisher {

    private final SimpMessageSendingOperations messagingTemplate;

    private static final String TOPIC_TEMPLATE = "/topic/reservations/{reservationId}/driver-location";

    /**
     * Publie la position reçue pour une réservation donnée.
     *
     * @param reservation réservation affectée au chauffeur (déjà vérifiée par
     *                    le contrôleur : chauffeur authentifié, réservation
     *                    EN_COURS, turn.driverId == driverId)
     * @param latitude    latitude GPS du chauffeur
     * @param longitude   longitude GPS du chauffeur
     * @param precision   description textuelle de la précision (optionnelle)
     */
    public void publish(Reservation reservation, double latitude, double longitude, String precision) {
        UUID reservationId = reservation.getId();
        String destination = TOPIC_TEMPLATE.replace("{reservationId}", reservationId.toString());

        DriverLocationMessage message = new DriverLocationMessage(latitude, longitude, precision);

        log.debug("Publication position chauffeur -> {} (lat={}, lng={}, precision={})",
                destination, latitude, longitude, precision != null ? precision : "non précisée");

        messagingTemplate.convertAndSend(destination, message);
    }

    /** DTO sérialisé dans le message STOMP reçu par le frontend. */
    public record DriverLocationMessage(
            double latitude,
            double longitude,
            String precision
    ) {
    }
}
