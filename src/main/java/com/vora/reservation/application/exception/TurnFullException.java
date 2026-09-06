package com.vora.reservation.application.exception;

public class TurnFullException extends RuntimeException{
    public TurnFullException(String message) {
        super(message);
    }
}
