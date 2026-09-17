package com.example.ratingsystem.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.math.BigDecimal;

@Entity
@Table(name = "grading_result_items")
class GradingResultItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grading_result_id", nullable = false)
    private GradingResultEntity gradingResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_item_id", nullable = false)
    private QuestionRubricItemEntity rubricItem;

    private BigDecimal maxScore;
    private BigDecimal suggestedScore;
    @Column(length = 1000)
    private String reason;

    protected GradingResultItemEntity() {
    }

    GradingResultItemEntity(QuestionRubricItemEntity rubricItem, BigDecimal maxScore,
                            BigDecimal suggestedScore, String reason) {
        this.rubricItem = rubricItem;
        this.maxScore = maxScore;
        this.suggestedScore = suggestedScore;
        this.reason = reason;
    }

    void setGradingResult(GradingResultEntity gradingResult) {
        this.gradingResult = gradingResult;
    }

    QuestionRubricItemEntity getRubricItem() {
        return rubricItem;
    }

    BigDecimal getMaxScore() {
        return maxScore;
    }

    BigDecimal getSuggestedScore() {
        return suggestedScore;
    }

    String getReason() {
        return reason;
    }
}
