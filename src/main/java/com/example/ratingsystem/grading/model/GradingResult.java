package com.example.ratingsystem.grading.model;

import java.math.BigDecimal;
import java.util.List;

public record GradingResult(
        Long questionId,
        BigDecimal maxScore,
        GradingStatus gradingStatus,
        BigDecimal suggestedScore,
        String reason,
        BigDecimal actualScore,
        ReviewStatus reviewStatus,
        List<CriterionScore> criterionScores,
        String failureMessage
) {
    public GradingResult {
        criterionScores = criterionScores == null ? List.of() : List.copyOf(criterionScores);
    }

    public static GradingResult pending(
            GradingRequest request,
            BigDecimal suggestedScore,
            String reason,
            List<CriterionScore> criterionScores
    ) {
        return new GradingResult(
                request.questionId(),
                request.maxScore(),
                GradingStatus.SUCCESS,
                suggestedScore,
                reason,
                null,
                ReviewStatus.PENDING,
                criterionScores,
                null
        );
    }

    public static GradingResult failed(GradingRequest request, String failureMessage) {
        return new GradingResult(
                request.questionId(),
                request.maxScore(),
                GradingStatus.FAILED,
                null,
                null,
                null,
                ReviewStatus.PENDING,
                List.of(),
                failureMessage
        );
    }
}
