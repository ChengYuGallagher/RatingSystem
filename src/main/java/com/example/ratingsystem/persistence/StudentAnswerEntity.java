package com.example.ratingsystem.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "student_answers")
class StudentAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ExamSubmissionEntity submission;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    @CreationTimestamp
    private Instant createdAt;

    protected StudentAnswerEntity() {
    }

    StudentAnswerEntity(Long examId, QuestionEntity question, String answerText) {
        this.examId = examId;
        this.question = question;
        this.answerText = answerText;
    }

    void setSubmission(ExamSubmissionEntity submission) {
        this.submission = submission;
    }

    Long getId() {
        return id;
    }

    ExamSubmissionEntity getSubmission() {
        return submission;
    }

    QuestionEntity getQuestion() {
        return question;
    }

    String getAnswerText() {
        return answerText;
    }
}
