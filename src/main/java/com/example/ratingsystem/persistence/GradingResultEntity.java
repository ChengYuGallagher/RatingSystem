package com.example.ratingsystem.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "grading_results")
class GradingResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_answer_id", nullable = false, unique = true)
    private StudentAnswerEntity studentAnswer;

    @Enumerated(EnumType.STRING)
    private GradingStatus gradingStatus;

    private BigDecimal suggestedScore;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 1000)
    private String failureMessage;
    private BigDecimal actualScore;

    @Enumerated(EnumType.STRING)
    private PersistentReviewStatus reviewStatus;

    private int attemptCount;
    private Instant runningSince;

    @OneToMany(mappedBy = "gradingResult", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<GradingResultItemEntity> items = new LinkedHashSet<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected GradingResultEntity() {
    }

    static GradingResultEntity running(StudentAnswerEntity answer, Instant now) {
        GradingResultEntity result = new GradingResultEntity();
        result.studentAnswer = answer;
        result.gradingStatus = GradingStatus.RUNNING;
        result.reviewStatus = PersistentReviewStatus.PENDING;
        result.attemptCount = 1;
        result.runningSince = now;
        return result;
    }

    void restart(Instant now) {
        if (reviewStatus == PersistentReviewStatus.CONFIRMED) {
            throw new PersistenceConflictException("已人工确认的评分结果不能重新自动评分");
        }
        gradingStatus = GradingStatus.RUNNING;
        suggestedScore = null;
        reason = null;
        failureMessage = null;
        runningSince = now;
        attemptCount++;
        items.clear();
    }

    void succeed(BigDecimal score, String gradingReason) {
        gradingStatus = GradingStatus.SUCCESS;
        suggestedScore = score;
        reason = gradingReason;
        failureMessage = null;
        runningSince = null;
    }

    void fail(String message) {
        gradingStatus = GradingStatus.FAILED;
        suggestedScore = null;
        reason = null;
        failureMessage = message;
        runningSince = null;
        items.clear();
    }

    void addItem(GradingResultItemEntity item) {
        items.add(item);
        item.setGradingResult(this);
    }

    Long getId() {
        return id;
    }

    StudentAnswerEntity getStudentAnswer() {
        return studentAnswer;
    }

    GradingStatus getGradingStatus() {
        return gradingStatus;
    }

    BigDecimal getSuggestedScore() {
        return suggestedScore;
    }

    String getReason() {
        return reason;
    }

    String getFailureMessage() {
        return failureMessage;
    }

    BigDecimal getActualScore() {
        return actualScore;
    }

    PersistentReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    Instant getRunningSince() {
        return runningSince;
    }

    Set<GradingResultItemEntity> getItems() {
        return items;
    }
}
