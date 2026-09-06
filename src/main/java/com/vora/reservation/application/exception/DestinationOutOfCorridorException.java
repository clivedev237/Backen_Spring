package com.vora.reservation.application.exception;

/**
 * La destination proposée n'a pas pu être vérifiée par Django Geo : adresse
 * non géocodable, ou totalement hors de la zone de couverture VORA
 * (cadrage §16). À ne pas confondre avec « aucun chauffeur compatible
 * disponible maintenant », qui n'est PAS une erreur (cadrage §5.1) : ce cas
 * ne lève pas cette exception, voir {@code ReservationService}.
 */
public class DestinationOutOfCorridorException extends RuntimeException {
    public DestinationOutOfCorridorException(String message) {
        super(message);
    }
}
