package com.example.ratingsystem.grading.ai;

import com.example.ratingsystem.grading.model.CriterionScore;
import com.example.ratingsystem.grading.model.GradingRequest;
import com.example.ratingsystem.grading.model.GradingResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

@Component
public class GradingResultValidator {

    private final ObjectMapper objectMapper;

    public GradingResultValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GradingResult validate(String rawResponse, GradingRequest request) {
        AiResult aiResult = parse(rawResponse);
        validateTotalScore(aiResult, request.maxScore());
        List<CriterionScore> criterionScores = validateCriteria(aiResult, request.maxScore());

        return GradingResult.pending(
                request,
                aiResult.suggestedScore(),
                aiResult.reason().trim(),
                criterionScores
        );
    }

    private AiResult parse(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new AiGradingException("AI 评分结果为空");
        }
        try {
            return objectMapper.readValue(rawResponse, AiResult.class);
        } catch (JacksonException exception) {
            throw new AiGradingException("AI 评分结果不是合法 JSON", exception);
        }
    }

    private void validateTotalScore(AiResult aiResult, BigDecimal questionMaxScore) {
        if (aiResult.suggestedScore() == null) {
            throw new AiGradingException("AI 评分结果缺少建议总分");
        }
        if (aiResult.suggestedScore().signum() < 0
                || aiResult.suggestedScore().compareTo(questionMaxScore) > 0) {
            throw new AiGradingException("AI 建议总分超出合法范围");
        }
        if (!StringUtils.hasText(aiResult.reason())) {
            throw new AiGradingException("AI 评分结果缺少总评理由");
        }
    }

    private List<CriterionScore> validateCriteria(AiResult aiResult, BigDecimal questionMaxScore) {
        if (aiResult.items() == null || aiResult.items().isEmpty()) {
            throw new AiGradingException("AI 评分结果缺少逐项评分");
        }

        BigDecimal totalItemMaxScore = BigDecimal.ZERO;
        BigDecimal totalItemScore = BigDecimal.ZERO;

        for (AiCriterion item : aiResult.items()) {
            if (!StringUtils.hasText(item.criterion()) || !StringUtils.hasText(item.reason())) {
                throw new AiGradingException("AI 逐项评分缺少评分点或理由");
            }
            if (item.maxScore() == null || item.score() == null
                    || item.maxScore().signum() <= 0
                    || item.score().signum() < 0
                    || item.score().compareTo(item.maxScore()) > 0) {
                throw new AiGradingException("AI 逐项评分超出合法范围");
            }
            totalItemMaxScore = totalItemMaxScore.add(item.maxScore());
            totalItemScore = totalItemScore.add(item.score());
        }

        if (totalItemMaxScore.compareTo(questionMaxScore) != 0) {
            throw new AiGradingException("AI 各评分项满分之和与题目满分不一致");
        }
        if (totalItemScore.compareTo(aiResult.suggestedScore()) != 0) {
            throw new AiGradingException("AI 各评分项得分之和与建议总分不一致");
        }

        return aiResult.items().stream()
                .map(item -> new CriterionScore(
                        item.criterion().trim(),
                        item.maxScore(),
                        item.score(),
                        item.reason().trim()
                ))
                .toList();
    }

    record AiResult(
            List<AiCriterion> items,
            BigDecimal suggestedScore,
            String reason
    ) {
    }

    record AiCriterion(
            String criterion,
            BigDecimal maxScore,
            BigDecimal score,
            String reason
    ) {
    }
}
