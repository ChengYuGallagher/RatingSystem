package com.example.ratingsystem.grading.service;

import com.example.ratingsystem.grading.model.FillBlankGradingMode;
import com.example.ratingsystem.grading.model.GradingRequest;
import com.example.ratingsystem.grading.model.GradingResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GradingService {

    private final ExactMatchGrader exactMatchGrader;
    private final AiGradingService aiGradingService;

    public GradingService(ExactMatchGrader exactMatchGrader, AiGradingService aiGradingService) {
        this.exactMatchGrader = exactMatchGrader;
        this.aiGradingService = aiGradingService;
    }

    public GradingResult grade(GradingRequest request) {
        return switch (request.questionType()) {
            case CHOICE, TRUE_FALSE -> exactMatchGrader.grade(request);
            case FILL_BLANK -> gradeFillBlank(request);
            case SHORT_ANSWER -> gradeWithAi(request);
        };
    }

    private GradingResult gradeFillBlank(GradingRequest request) {
        FillBlankGradingMode mode = request.fillBlankGradingMode() == null
                ? FillBlankGradingMode.EXACT
                : request.fillBlankGradingMode();
        return mode == FillBlankGradingMode.AI ? gradeWithAi(request) : exactMatchGrader.grade(request);
    }

    private GradingResult gradeWithAi(GradingRequest request) {
        if (!StringUtils.hasText(request.gradingCriteria()) && request.rubricItems().isEmpty()) {
            throw new InvalidGradingRequestException("AI 评分必须提供 gradingCriteria 或 rubricItems");
        }
        return aiGradingService.grade(request);
    }
}
