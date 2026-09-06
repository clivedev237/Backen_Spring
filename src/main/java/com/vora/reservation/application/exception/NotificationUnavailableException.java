package com.vora.reservation.application.exception;

/**
 * Exception technique : le webhook de notification frontend est indisponible.
 *
 * <p>Cette exception N'impacte pas le flux métier (best-effort, Phase 9).
 * Elle est loguée et l'exécution continue. Elle n'est pas exposée en HTTP
 * vers les clients du microservice.
 */
public class NotificationUnavailableException extends RuntimeException {

    public NotificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationUnavailableException(String message) {
        super(message);
    }
}
