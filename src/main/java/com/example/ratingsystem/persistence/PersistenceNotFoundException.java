package com.example.ratingsystem.persistence;

public class PersistenceNotFoundException extends RuntimeException {
    public PersistenceNotFoundException(String message) {
        super(message);
    }
}
