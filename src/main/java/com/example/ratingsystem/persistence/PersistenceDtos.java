package com.example.ratingsystem.persistence;

import com.example.ratingsystem.grading.model.FillBlankGradingMode;
import com.example.ratingsystem.grading.model.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class PersistenceDtos {

    private PersistenceDtos() {
    }

    public record CreateExamRequest(
            @NotBlank @Size(max = 200) String name,
            @NotEmpty List<@Valid QuestionInput> questions
    ) {
    }

    public record QuestionInput(
            @Positive int questionNo,
            @NotNull QuestionType questionType,
            @NotBlank @Size(max = 10_000) String content,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal maxScore,
            @NotBlank @Size(max = 10_000) String referenceAnswer,
            @Size(max = 10_000) String gradingCriteria,
            FillBlankGradingMode fillBlankGradingMode,
            List<@Valid RubricItemInput> rubricItems
    ) {
        public QuestionInput {
            rubricItems = rubricItems == null ? List.of() : List.copyOf(rubricItems);
        }
    }

    public record RubricItemInput(
            @Positive int itemOrder,
            @NotBlank @Size(max = 500) String name,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal maxScore
    ) {
    }

    public record ExamView(Long id, String name, ExamStatus status, List<QuestionView> questions) {
    }

    public record QuestionView(
            Long id,
            int questionNo,
            QuestionType questionType,
            BigDecimal maxScore,
            List<RubricItemView> rubricItems
    ) {
    }

    public record RubricItemView(Long id, int itemOrder, String name, BigDecimal maxScore) {
    }

    public record CreateSubmissionRequest(
            @NotBlank @Size(max = 100) String studentNo,
            @NotBlank @Size(max = 100) String studentName,
            @NotEmpty List<@Valid AnswerInput> answers
    ) {
    }

    public record AnswerInput(
            @NotNull @Positive Long questionId,
            @NotNull @Size(max = 20_000) String answerText
    ) {
    }

    public record SubmissionView(
            Long id,
            Long examId,
            Long studentId,
            String studentNo,
            String studentName,
            int answerCount
    ) {
    }

    public record GradeSubmissionView(Long submissionId, List<GradingResultView> results) {
    }

    public record GradingResultView(
            Long id,
            Long answerId,
            Long questionId,
            int questionNo,
            QuestionType questionType,
            String question,
            BigDecimal maxScore,
            String referenceAnswer,
            String gradingCriteria,
            List<RubricItemView> rubricItems,
            String studentAnswer,
            GradingStatus gradingStatus,
            BigDecimal suggestedScore,
            String reason,
            String failureMessage,
            BigDecimal actualScore,
            PersistentReviewStatus reviewStatus,
            int attemptCount,
            List<CriterionResultView> criterionScores
    ) {
    }

    public record CriterionResultView(
            Long rubricItemId,
            String criterion,
            BigDecimal maxScore,
            BigDecimal suggestedScore,
            String reason
    ) {
    }
}
