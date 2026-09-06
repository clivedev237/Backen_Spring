package com.vora.reservation.application.exception;

/** Erreur métier Phase 6 : le Turn associé à une réservation acceptée est introuvable. */
public class TurnNotFoundException extends RuntimeException {
    public TurnNotFoundException(String message) {
        super(message);
    }
}
