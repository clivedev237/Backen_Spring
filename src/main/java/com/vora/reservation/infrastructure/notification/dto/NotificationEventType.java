package com.vora.reservation.infrastructure.notification.dto;

/**
 * Types d'événements de notification envoyés au frontend via webhook (Phase 9).
 *
 * <p>Déclencheurs métier identifiés :
 * - {@code RESERVATION_ASSIGNED} : un chauffeur vient d'accepter la réservation
 *   (étape 6 du workflow, après acceptation d'offre).
 * - {@code DRIVER_STARTED} : le chauffeur a démarré la course (étape 9).
 * - {@code PAYMENT_INITIATED} : le paiement a été initié chez Node (étape 12).
 * - {@code PAYMENT_SUCCEEDED} : le paiement est confirmé (étape 13).
 * - {@code RESERVATION_AVAILABLE} : une offre vient d'être diffusée à un
 *   chauffeur (offre disponible pour acceptation, étape 5).
 * - {@code ARRIVAL_CONFIRMED} : le passager a confirmé son arrivée (étape 11).
 *
 * <p>À étendre si le workflow évolue (annulation, échec de paiement, etc.).
 */
public enum NotificationEventType {

    /** Un chauffeur vient d'accepter la réservation — attribution. (étape 6) */
    RESERVATION_ASSIGNED,

    /** Le chauffeur a démarré la course (passager à bord). (étape 9) */
    DRIVER_STARTED,

    /** Le paiement a été initié chez Node Auth & Payment. (étape 12) */
    PAYMENT_INITIATED,

    /** Le paiement est confirmé avec succès. (étape 13) */
    PAYMENT_SUCCEEDED,

    /** Une offre vient d'être diffusée — réservation disponible pour un chauffeur. (étape 5) */
    RESERVATION_AVAILABLE,

    /** Le passager a confirmé son arrivée. (étape 11) */
    ARRIVAL_CONFIRMED
}
