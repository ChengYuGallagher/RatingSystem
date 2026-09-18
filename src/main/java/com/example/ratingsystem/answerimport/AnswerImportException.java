package com.example.ratingsystem.answerimport;

public class AnswerImportException extends RuntimeException {

    public AnswerImportException(String message) {
        super(message);
    }

    public AnswerImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
