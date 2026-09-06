package com.vora.reservation.infrastructure.notification.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.UUID;

/**
 * Payload envoyé au frontend via webhook (Phase 9, canal = webhook front).
 *
 * <p>Le frontend s'abonne à la réservation concernée côté client et traite
 * l'événement selon son {@code type}. Le {@code data} est libre pour chaque
 * type d'événement ; le contrat exact des payloads est à finaliser avec le
 * frontend (React PWA, Vercel — cadrage §2).
 */
@Value
@Builder
public class NotificationEvent {

    /** Identifiant unique de l'événement (uuid, utile pour le tracking frontend). */
    UUID eventId;

    /** Type d'événement métier. */
    NotificationEventType type;

    /** Identifiant de la réservation concernée (la plupart des événements). */
    UUID reservationId;

    /** Identifiant du turn si pertinent (assignation, start, arrivée, paiement). */
    UUID turnId;

    /** Identifiant du client (passager) concerné. */
    Long clientId;

    /** Identifiant du chauffeur si pertinent (assignation, start). */
    Long driverId;

    /** Horodatage de l'événement métier (pas de l'envoi HTTP). */
    Long timestamp;

    /** Payload libre spécifique au type d'événement. */
    Map<String, Object> data;

    /**
     * Constructeur rapide depuis un builder partiel.
     */
    public static NotificationEvent of(NotificationEventType type, UUID reservationId) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .type(type)
                .reservationId(reservationId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
