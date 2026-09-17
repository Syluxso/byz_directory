package com.nyberg.directory.intel;

/** 4xx other than 429 — persist error, do not retry immediately. */
public class MaxMindPermanentException extends RuntimeException {
    public MaxMindPermanentException(String message) {
        super(message);
    }
}
