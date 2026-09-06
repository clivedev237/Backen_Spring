package com.vora.reservation.application.exception;

public class PaymentAlreadyInitiatedException extends RuntimeException {

    public PaymentAlreadyInitiatedException(String message) {
        super(message);
    }
}
