package com.vora.reservation.application.exception;

/** Erreur métier Phase 6 : le passager tente de confirmer l'arrivée sans être à bord. */
public class ReservationNotStartedException extends RuntimeException {
    public ReservationNotStartedException(String message) {
        super(message);
    }
}
