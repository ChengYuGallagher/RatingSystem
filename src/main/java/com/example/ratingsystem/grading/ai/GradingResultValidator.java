package com.example.ratingsystem.grading.ai;

import com.example.ratingsystem.grading.model.CriterionScore;
import com.example.ratingsystem.grading.model.GradingRequest;
import com.example.ratingsystem.grading.model.GradingResult;
import com.example.ratingsystem.grading.model.RubricItem;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GradingResultValidator {

    private final ObjectMapper objectMapper;

    public GradingResultValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GradingResult validate(String rawResponse, GradingRequest request) {
        AiResult aiResult = parse(rawResponse);
        validateTotalScore(aiResult, request.maxScore());
        List<CriterionScore> criterionScores = validateCriteria(aiResult, request);

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

    private List<CriterionScore> validateCriteria(AiResult aiResult, GradingRequest request) {
        if (aiResult.items() == null || aiResult.items().isEmpty()) {
            throw new AiGradingException("AI 评分结果缺少逐项评分");
        }

        BigDecimal totalItemMaxScore = BigDecimal.ZERO;
        BigDecimal totalItemScore = BigDecimal.ZERO;
        boolean structuredRubric = !request.rubricItems().isEmpty();
        Map<Long, RubricItem> rubricById = request.rubricItems().stream()
                .collect(Collectors.toMap(RubricItem::id, Function.identity(), (left, right) -> {
                    throw new AiGradingException("评分规则包含重复 rubricItemId");
                }));
        Set<Long> returnedIds = new java.util.HashSet<>();

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
            if (structuredRubric) {
                RubricItem rubricItem = rubricById.get(item.rubricItemId());
                if (rubricItem == null || !returnedIds.add(item.rubricItemId())) {
                    throw new AiGradingException("AI 返回了未知或重复的 rubricItemId");
                }
                if (item.maxScore().compareTo(rubricItem.maxScore()) != 0) {
                    throw new AiGradingException("AI 返回的评分项满分与评分规则不一致");
                }
            }
            totalItemMaxScore = totalItemMaxScore.add(item.maxScore());
            totalItemScore = totalItemScore.add(item.score());
        }

        if (structuredRubric && !returnedIds.equals(rubricById.keySet())) {
            throw new AiGradingException("AI 返回的评分点集合与评分规则不一致");
        }
        if (totalItemMaxScore.compareTo(request.maxScore()) != 0) {
            throw new AiGradingException("AI 各评分项满分之和与题目满分不一致");
        }
        if (totalItemScore.compareTo(aiResult.suggestedScore()) != 0) {
            throw new AiGradingException("AI 各评分项得分之和与建议总分不一致");
        }

        return aiResult.items().stream()
                .map(item -> new CriterionScore(
                        item.rubricItemId(),
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
            Long rubricItemId,
            String criterion,
            BigDecimal maxScore,
            BigDecimal score,
            String reason
    ) {
    }
}
