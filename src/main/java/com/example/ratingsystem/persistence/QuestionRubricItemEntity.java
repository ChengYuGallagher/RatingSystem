package com.example.ratingsystem.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "question_rubric_items")
class QuestionRubricItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    private int itemOrder;
    private String name;
    private BigDecimal maxScore;

    protected QuestionRubricItemEntity() {
    }

    QuestionRubricItemEntity(int itemOrder, String name, BigDecimal maxScore) {
        this.itemOrder = itemOrder;
        this.name = name;
        this.maxScore = maxScore;
    }

    void setQuestion(QuestionEntity question) {
        this.question = question;
    }

    void update(String name, BigDecimal maxScore) {
        this.name = name;
        this.maxScore = maxScore;
    }

    Long getId() {
        return id;
    }

    int getItemOrder() {
        return itemOrder;
    }

    String getName() {
        return name;
    }

    BigDecimal getMaxScore() {
        return maxScore;
    }
}
