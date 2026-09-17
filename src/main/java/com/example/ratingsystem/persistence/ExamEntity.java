package com.example.ratingsystem.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exams")
class ExamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private ExamStatus status = ExamStatus.DRAFT;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionEntity> questions = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected ExamEntity() {
    }

    ExamEntity(String name) {
        this.name = name;
    }

    void addQuestion(QuestionEntity question) {
        questions.add(question);
        question.setExam(this);
    }

    void startScoring() {
        status = ExamStatus.SCORING;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    ExamStatus getStatus() {
        return status;
    }

    List<QuestionEntity> getQuestions() {
        return questions;
    }
}
