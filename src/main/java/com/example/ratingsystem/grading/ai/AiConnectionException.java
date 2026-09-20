package com.example.ratingsystem.grading.ai;

public class AiConnectionException extends RuntimeException {

    public AiConnectionException(String message) {
        super(message);
    }

    public AiConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
