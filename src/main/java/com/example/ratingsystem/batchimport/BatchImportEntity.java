package com.example.ratingsystem.batchimport;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "answer_import_batches")
class BatchImportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long examId;
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    private BatchImportStatus status = BatchImportStatus.REVIEWING;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BatchImportStudentEntity> students = new ArrayList<>();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BatchImportIssueEntity> issues = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected BatchImportEntity() {
    }

    BatchImportEntity(Long examId, String originalFilename) {
        this.examId = examId;
        this.originalFilename = originalFilename;
    }

    void addStudent(BatchImportStudentEntity student) {
        students.add(student);
        student.setBatch(this);
    }

    void addIssue(BatchImportIssueEntity issue) {
        issues.add(issue);
        issue.setBatch(this);
    }

    void completeIfAllImported() {
        if (!students.isEmpty() && students.stream()
                .allMatch(student -> student.getReviewStatus() == BatchReviewStatus.IMPORTED)) {
            status = BatchImportStatus.COMPLETED;
        }
    }

    Long getId() {
        return id;
    }

    Long getExamId() {
        return examId;
    }

    String getOriginalFilename() {
        return originalFilename;
    }

    BatchImportStatus getStatus() {
        return status;
    }

    List<BatchImportStudentEntity> getStudents() {
        return students;
    }

    List<BatchImportIssueEntity> getIssues() {
        return issues;
    }
}
