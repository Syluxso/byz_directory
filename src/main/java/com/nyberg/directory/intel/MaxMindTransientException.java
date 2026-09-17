package com.nyberg.directory.intel;

/** 429 / 5xx — Kafka should retry. */
public class MaxMindTransientException extends RuntimeException {
    public MaxMindTransientException(String message) {
        super(message);
    }

    public MaxMindTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
