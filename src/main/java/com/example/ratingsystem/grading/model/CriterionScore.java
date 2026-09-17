package com.example.ratingsystem.grading.model;

import java.math.BigDecimal;

public record CriterionScore(
        Long rubricItemId,
        String criterion,
        BigDecimal maxScore,
        BigDecimal suggestedScore,
        String reason
) {
}
