package com.example.ratingsystem.persistence;

public class PersistenceConflictException extends RuntimeException {
    public PersistenceConflictException(String message) {
        super(message);
    }
}
