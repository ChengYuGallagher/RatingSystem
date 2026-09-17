package com.example.ratingsystem.grading.service;

import com.example.ratingsystem.grading.model.GradingRequest;
import com.example.ratingsystem.grading.model.GradingResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class ExactMatchGrader {

    public GradingResult grade(GradingRequest request) {
        boolean correct = normalize(request.referenceAnswer()).equals(normalize(request.studentAnswer()));
        BigDecimal score = correct ? request.maxScore() : BigDecimal.ZERO;
        String reason = correct ? "学生答案与标准答案一致" : "学生答案与标准答案不一致";
        return GradingResult.pending(request, score, reason, List.of());
    }

    private String normalize(String answer) {
        return Normalizer.normalize(answer, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
