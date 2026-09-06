package com.vora.reservation.application.exception;

/** Erreur métier Phase 6 : la réservation n'est pas encore acceptée par un chauffeur. */
public class ReservationNotAcceptedException extends RuntimeException {
    public ReservationNotAcceptedException(String message) {
        super(message);
    }
}
