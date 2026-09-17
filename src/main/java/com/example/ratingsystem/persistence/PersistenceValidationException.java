package com.example.ratingsystem.persistence;

public class PersistenceValidationException extends RuntimeException {
    public PersistenceValidationException(String message) {
        super(message);
    }
}
