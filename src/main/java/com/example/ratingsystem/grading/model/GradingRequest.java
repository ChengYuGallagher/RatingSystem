package com.example.ratingsystem.grading.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GradingRequest(
        @NotNull @Positive Long questionId,
        @NotNull QuestionType questionType,
        @NotBlank @Size(max = 10_000) String question,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal maxScore,
        @NotBlank @Size(max = 10_000) String referenceAnswer,
        @Size(max = 10_000) String gradingCriteria,
        @NotNull @Size(max = 20_000) String studentAnswer,
        FillBlankGradingMode fillBlankGradingMode
) {
}
