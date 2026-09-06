package com.vora.reservation.application.exception;

public class OfferExpiredException extends RuntimeException{
    public OfferExpiredException(String message) {
        super(message);
    }
}
