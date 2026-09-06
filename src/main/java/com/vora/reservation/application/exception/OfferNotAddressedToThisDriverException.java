package com.vora.reservation.application.exception;

/** Erreur métier Phase 6 : l'offre n'a pas été diffusée à ce chauffeur. */
public class OfferNotAddressedToThisDriverException extends RuntimeException {
    public OfferNotAddressedToThisDriverException(String message) {
        super(message);
    }
}
