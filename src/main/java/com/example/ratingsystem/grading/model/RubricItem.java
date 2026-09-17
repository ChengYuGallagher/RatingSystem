package com.example.ratingsystem.grading.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RubricItem(
        @NotNull @Positive Long id,
        @NotBlank @Size(max = 500) String name,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal maxScore
) {
}
