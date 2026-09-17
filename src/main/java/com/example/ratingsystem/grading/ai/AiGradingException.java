package com.example.ratingsystem.grading.ai;

public class AiGradingException extends RuntimeException {

    public AiGradingException(String message) {
        super(message);
    }

    public AiGradingException(String message, Throwable cause) {
        super(message, cause);
    }
}
