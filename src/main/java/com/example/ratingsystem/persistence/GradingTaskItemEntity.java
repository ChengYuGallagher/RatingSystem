package com.example.ratingsystem.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "grading_task_items")
class GradingTaskItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private GradingTaskEntity task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ExamSubmissionEntity submission;

    @Enumerated(EnumType.STRING)
    private GradingTaskItemStatus status = GradingTaskItemStatus.PENDING;

    private int attemptCount;
    private String errorMessage;

    @UpdateTimestamp
    private Instant updatedAt;

    protected GradingTaskItemEntity() {
    }

    GradingTaskItemEntity(ExamSubmissionEntity submission) {
        this.submission = submission;
    }

    void setTask(GradingTaskEntity task) {
        this.task = task;
    }

    void start() {
        status = GradingTaskItemStatus.RUNNING;
        attemptCount++;
        errorMessage = null;
    }

    void succeed() {
        status = GradingTaskItemStatus.SUCCESS;
        errorMessage = null;
    }

    void fail(String message) {
        status = GradingTaskItemStatus.FAILED;
        errorMessage = message;
    }

    void prepareRetry() {
        status = GradingTaskItemStatus.PENDING;
        errorMessage = null;
    }

    void recoverInterruptedRun() {
        if (status == GradingTaskItemStatus.RUNNING) {
            status = GradingTaskItemStatus.PENDING;
            errorMessage = "检测到上次批量评分异常中断，已等待显式续跑";
        }
    }

    Long getId() {
        return id;
    }

    GradingTaskEntity getTask() {
        return task;
    }

    ExamSubmissionEntity getSubmission() {
        return submission;
    }

    GradingTaskItemStatus getStatus() {
        return status;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    String getErrorMessage() {
        return errorMessage;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
