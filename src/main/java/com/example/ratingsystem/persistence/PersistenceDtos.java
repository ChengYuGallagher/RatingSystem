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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
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

    public record UpdateQuestionStandardsRequest(
            @NotBlank @Size(max = 10_000) String referenceAnswer,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal maxScore,
            @Size(max = 10_000) String gradingCriteria,
            List<@Valid RubricItemInput> rubricItems
    ) {
        public UpdateQuestionStandardsRequest {
            rubricItems = rubricItems == null ? List.of() : List.copyOf(rubricItems);
        }
    }

    public record ExamView(Long id, String name, ExamStatus status, boolean standardsReviewed,
                           Instant standardsReviewedAt, List<QuestionView> questions) {
    }

    public record QuestionView(
            Long id,
            int questionNo,
            QuestionType questionType,
            String content,
            BigDecimal maxScore,
            String referenceAnswer,
            String gradingCriteria,
            FillBlankGradingMode fillBlankGradingMode,
            List<RubricItemView> rubricItems
    ) {
    }

    public record ExamListItemView(
            Long id,
            String name,
            ExamStatus status,
            boolean standardsReviewed,
            int questionCount,
            BigDecimal maxScore
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

    public enum ReviewAction {
        ACCEPT_SUGGESTION,
        SET_SCORE
    }

    public record ReviewRequest(
            @NotNull ReviewAction action,
            @DecimalMin(value = "0") @Digits(integer = 6, fraction = 2) BigDecimal actualScore,
            @NotNull @PositiveOrZero Long expectedVersion
    ) {
    }

    public enum ScoreSummaryStatus {
        INCOMPLETE,
        COMPLETE
    }

    public record SubmissionScoreSummaryView(
            Long examId,
            String examName,
            Long submissionId,
            Long studentId,
            String studentNo,
            String studentName,
            BigDecimal examMaxScore,
            int questionCount,
            int answerRecordCount,
            int gradingResultCount,
            int successfullyGradedQuestionCount,
            int confirmedQuestionCount,
            int unconfirmedQuestionCount,
            BigDecimal confirmedScore,
            ScoreSummaryStatus completionStatus,
            BigDecimal finalScore
    ) {
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
            long version,
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
