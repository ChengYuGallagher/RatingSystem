package com.example.ratingsystem.examimport;

public class ExamPaperImportException extends RuntimeException {

    public ExamPaperImportException(String message) {
        super(message);
    }

    public ExamPaperImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
