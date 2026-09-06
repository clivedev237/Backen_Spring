package com.vora.reservation.application.exception;

public class OfferAlreadyResolvedException extends RuntimeException{
    public OfferAlreadyResolvedException(String message) {
        super(message);
    }
}
