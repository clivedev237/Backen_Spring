package com.vora.reservation.domain.enums;

/**
 * Statuts d'une {@code ReservationOffer} diffusée à un chauffeur.
 * Durée de validité confirmée : 5 minutes à partir de la diffusion.
 */
public enum OfferStatus {
    EN_ATTENTE,
    ACCEPTEE,
    REFUSEE,
    EXPIREE,
    /** Invalidée car un autre chauffeur a été retenu en premier (concurrence). */
    INVALIDEE
}
