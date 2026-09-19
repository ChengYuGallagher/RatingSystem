package com.example.ratingsystem.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grading_tasks")
class GradingTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @Enumerated(EnumType.STRING)
    private GradingTaskStatus status = GradingTaskStatus.RUNNING;

    private int totalCount;
    private int processedCount;
    private int successCount;
    private int failedCount;

    @CreationTimestamp
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<GradingTaskItemEntity> items = new ArrayList<>();

    protected GradingTaskEntity() {
    }

    GradingTaskEntity(ExamEntity exam, Instant now) {
        this.exam = exam;
        this.startedAt = now;
    }

    void addItem(GradingTaskItemEntity item) {
        items.add(item);
        item.setTask(this);
        totalCount = items.size();
    }

    void restart(Instant now) {
        status = GradingTaskStatus.RUNNING;
        startedAt = now;
        completedAt = null;
        refreshCounts();
    }

    void refreshCounts() {
        totalCount = items.size();
        successCount = (int) items.stream().filter(item -> item.getStatus() == GradingTaskItemStatus.SUCCESS).count();
        failedCount = (int) items.stream().filter(item -> item.getStatus() == GradingTaskItemStatus.FAILED).count();
        processedCount = successCount + failedCount;
        if (totalCount > 0 && processedCount == totalCount) {
            status = failedCount == 0 ? GradingTaskStatus.COMPLETED : GradingTaskStatus.PARTIAL_FAILED;
            completedAt = Instant.now();
        }
    }

    Long getId() {
        return id;
    }

    ExamEntity getExam() {
        return exam;
    }

    GradingTaskStatus getStatus() {
        return status;
    }

    int getTotalCount() {
        return totalCount;
    }

    int getProcessedCount() {
        return processedCount;
    }

    int getSuccessCount() {
        return successCount;
    }

    int getFailedCount() {
        return failedCount;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    List<GradingTaskItemEntity> getItems() {
        return items;
    }
}
