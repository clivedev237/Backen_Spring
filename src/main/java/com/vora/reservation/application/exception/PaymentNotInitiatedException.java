package com.vora.reservation.application.exception;

public class PaymentNotInitiatedException extends RuntimeException {

    public PaymentNotInitiatedException(String message) {
        super(message);
    }
}
