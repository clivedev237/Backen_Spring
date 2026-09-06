package com.vora.reservation.application.exception;

import java.util.UUID;

public class ReservationOfferNotFoundException extends RuntimeException{
    public ReservationOfferNotFoundException(UUID id) {
        super("Aucune offre trouvée avec l'identifiant " + id);
    }
}
