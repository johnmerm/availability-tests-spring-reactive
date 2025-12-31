package com.booking.exception;

public class NoShardsAvailableException extends RuntimeException {
    public NoShardsAvailableException(String message) {
        super(message);
    }
}
