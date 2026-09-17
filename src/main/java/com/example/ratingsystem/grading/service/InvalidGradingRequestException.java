package com.example.ratingsystem.grading.service;

public class InvalidGradingRequestException extends RuntimeException {

    public InvalidGradingRequestException(String message) {
        super(message);
    }
}
