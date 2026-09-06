package com.vora.reservation.domain.enums;

/**
 * Statuts d'une {@code Reservation} (par passager). Voir cadrage §7.1.
 */
public enum ReservationStatus {
    /** Créée, en attente d'un chauffeur compatible. */
    EN_ATTENTE,
    /** Une ou plusieurs offres ont été diffusées à des chauffeurs candidats. */
    DIFFUSEE,
    /** Un chauffeur a accepté l'offre ; le passager est inséré dans un Turn. */
    ACCEPTEE,
    /** Le passager est à bord (start effectué). */
    EN_COURS,
    /** Le passager a confirmé son arrivée. */
    ARRIVEE_CONFIRMEE,
    /** Paiement en traitement (délégué au service Auth &amp; Payment). */
    PAIEMENT_EN_COURS,
    /** Course et paiement finalisés pour ce passager. */
    TERMINEE,
    /** Réservation annulée par le client. */
    ANNULEE,
    /** Aucun chauffeur n'a accepté dans le délai (5 minutes, confirmé). */
    EXPIREE,
    /** Paiement numérique non abouti. */
    PAIEMENT_ECHOUE
}
