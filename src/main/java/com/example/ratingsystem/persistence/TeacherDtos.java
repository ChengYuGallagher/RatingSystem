package com.example.ratingsystem.persistence;

import com.example.ratingsystem.grading.model.QuestionType;

import java.math.BigDecimal;
import java.util.List;

public final class TeacherDtos {

    private TeacherDtos() {
    }

    public record SubmissionListItemView(
            Long submissionId,
            Long studentId,
            String studentNo,
            String studentName,
            int answerCount
    ) {
    }

    public record ClassResultsView(
            Long examId,
            String examName,
            boolean standardsReviewed,
            List<ClassQuestionView> questions,
            List<ClassSubmissionView> submissions
    ) {
    }

    public record ClassQuestionView(
            Long questionId,
            int questionNo,
            QuestionType questionType,
            BigDecimal maxScore
    ) {
    }

    public record ClassSubmissionView(
            Long submissionId,
            Long studentId,
            String studentNo,
            String studentName,
            PersistenceDtos.ScoreSummaryStatus completionStatus,
            int confirmedQuestionCount,
            int questionCount,
            BigDecimal confirmedScore,
            BigDecimal finalScore,
            List<QuestionScoreView> questionScores
    ) {
    }

    public record QuestionScoreView(
            Long questionId,
            int questionNo,
            GradingStatus gradingStatus,
            PersistentReviewStatus reviewStatus,
            BigDecimal suggestedScore,
            BigDecimal actualScore,
            String failureMessage
    ) {
    }
}
