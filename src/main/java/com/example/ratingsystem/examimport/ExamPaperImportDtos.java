package com.example.ratingsystem.examimport;

import com.example.ratingsystem.grading.model.FillBlankGradingMode;
import com.example.ratingsystem.grading.model.QuestionType;

import java.math.BigDecimal;
import java.util.List;

public final class ExamPaperImportDtos {

    private ExamPaperImportDtos() {
    }

    public enum AnswerSource {
        ORIGINAL_DOCUMENT,
        REFERENCE_DOCUMENT,
        AI_INFERRED,
        MISSING
    }

    public record ExamDraftView(
            String suggestedName,
            String sourceFilename,
            String referenceFilename,
            int sourceBlockCount,
            boolean needsReview,
            List<ExamDraftQuestionView> questions,
            List<String> issues
    ) {
    }

    public record ExamDraftQuestionView(
            int questionNo,
            QuestionType questionType,
            String content,
            BigDecimal maxScore,
            String referenceAnswer,
            String gradingCriteria,
            FillBlankGradingMode fillBlankGradingMode,
            List<ExamDraftRubricView> rubricItems,
            AnswerSource answerSource,
            boolean needsReview,
            List<String> reviewNotes
    ) {
    }

    public record ExamDraftRubricView(
            int itemOrder,
            String name,
            BigDecimal maxScore
    ) {
    }
}
