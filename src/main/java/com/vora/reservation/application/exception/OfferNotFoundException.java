package com.vora.reservation.application.exception;

/** Errreur métier Phase 6 : l'offre associée à une réservation acceptée n'est pas trouée. */
public class OfferNotFoundException extends RuntimeException {
    public OfferNotFoundException(String message) {
        super(message);
    }
}
