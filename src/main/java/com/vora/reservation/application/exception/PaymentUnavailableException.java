package com.vora.reservation.application.exception;

public class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentUnavailableException(String message) {
        super(message);
    }
}
