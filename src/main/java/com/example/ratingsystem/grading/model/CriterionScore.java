package com.example.ratingsystem.grading.model;

import java.math.BigDecimal;

public record CriterionScore(
        String criterion,
        BigDecimal maxScore,
        BigDecimal suggestedScore,
        String reason
) {
}
