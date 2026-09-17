package com.example.ratingsystem.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_submissions")
class ExamSubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentEntity student;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StudentAnswerEntity> answers = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    protected ExamSubmissionEntity() {
    }

    ExamSubmissionEntity(ExamEntity exam, StudentEntity student) {
        this.exam = exam;
        this.student = student;
    }

    void addAnswer(StudentAnswerEntity answer) {
        answers.add(answer);
        answer.setSubmission(this);
    }

    Long getId() {
        return id;
    }

    ExamEntity getExam() {
        return exam;
    }

    StudentEntity getStudent() {
        return student;
    }

    List<StudentAnswerEntity> getAnswers() {
        return answers;
    }
}
