package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.answerimport.AnswerImportDtos.ImportQuestionType;
import com.example.ratingsystem.answerimport.AnswerImportDtos.ParseStatus;
import com.example.ratingsystem.grading.model.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class BatchImportDtos {

    private BatchImportDtos() {
    }

    public record BatchImportView(
            Long id,
            Long examId,
            String originalFilename,
            BatchImportStatus status,
            int uploadedStudentCount,
            int parseSuccessCount,
            int needsReviewCount,
            int parseFailedCount,
            int confirmedCount,
            int importedCount,
            List<BatchStudentView> students,
            List<BatchIssueView> issues
    ) {
    }

    public record BatchStudentView(
            Long id,
            String sourcePath,
            String detectedStudentNo,
            String detectedStudentName,
            String studentNo,
            String studentName,
            int expectedQuestionCount,
            int recognizedQuestionCount,
            ParseStatus parseStatus,
            BatchReviewStatus reviewStatus,
            Long submissionId,
            long version,
            List<BatchAnswerView> answers,
            List<BatchIssueView> issues
    ) {
    }

    public record BatchAnswerView(
            Long id,
            int answerOrder,
            Long questionId,
            Integer questionNo,
            QuestionType questionType,
            Integer sourceQuestionNo,
            ImportQuestionType sourceQuestionType,
            String rawAnswer,
            Integer sourceStartBlock,
            Integer sourceEndBlock,
            ParseStatus parseStatus,
            List<BatchIssueView> issues
    ) {
    }

    public record BatchIssueView(String code, String message, Integer answerOrder) {
    }

    public record UpdateBatchStudentRequest(
            @NotBlank @Size(max = 100) String studentNo,
            @NotBlank @Size(max = 100) String studentName,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotEmpty List<@Valid BatchAnswerCorrection> answers
    ) {
    }

    public record BatchAnswerCorrection(
            @NotNull @Positive Long answerId,
            @NotNull @Positive Long questionId,
            @NotNull @Size(max = 20_000) String answerText
    ) {
    }

    public record ConfirmBatchStudentRequest(@NotNull @PositiveOrZero Long expectedVersion) {
    }

    public record BatchImportExecutionView(
            Long batchId,
            int importedCount,
            int remainingConfirmedCount,
            List<ImportedSubmissionView> imported,
            List<BatchIssueView> failures
    ) {
    }

    public record ImportedSubmissionView(Long studentImportId, Long submissionId,
                                         String studentNo, String studentName) {
    }
}
