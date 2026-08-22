package com.hammerly.worker.processing;

public class UnsupportedEventTypeException extends RuntimeException {
    public UnsupportedEventTypeException(String message) {
        super(message);
    }
}
