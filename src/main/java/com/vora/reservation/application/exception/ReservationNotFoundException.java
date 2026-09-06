package com.vora.reservation.application.exception;

import java.util.UUID;
public class ReservationNotFoundException extends RuntimeException{
    public ReservationNotFoundException(UUID id) {
        super("Aucune réservation trouvée avec l'identifiant " + id);
    }
}
