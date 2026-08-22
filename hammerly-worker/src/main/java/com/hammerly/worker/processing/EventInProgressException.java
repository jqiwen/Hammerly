package com.hammerly.worker.processing;

public class EventInProgressException extends RuntimeException {
    public EventInProgressException(String message) {
        super(message);
    }
}
