package com.example.ratingsystem.batchimport;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "answer_import_issues")
class BatchImportIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private BatchImportEntity batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_import_id")
    private BatchImportStudentEntity studentImport;

    private Integer answerOrder;
    private String code;
    @Column(columnDefinition = "TEXT")
    private String message;

    protected BatchImportIssueEntity() {
    }

    BatchImportIssueEntity(BatchImportStudentEntity studentImport, Integer answerOrder,
                           String code, String message) {
        this.studentImport = studentImport;
        this.answerOrder = answerOrder;
        this.code = code;
        this.message = message;
    }

    void setBatch(BatchImportEntity batch) {
        this.batch = batch;
    }

    BatchImportStudentEntity getStudentImport() {
        return studentImport;
    }

    Integer getAnswerOrder() {
        return answerOrder;
    }

    String getCode() {
        return code;
    }

    String getMessage() {
        return message;
    }
}
