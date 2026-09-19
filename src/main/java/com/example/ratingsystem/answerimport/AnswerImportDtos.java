package com.example.ratingsystem.answerimport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AnswerImportDtos {

    private AnswerImportDtos() {
    }

    public enum ImportQuestionType {
        CHOICE,
        FILL_BLANK,
        TRUE_FALSE,
        SHORT_ANSWER,
        PROGRAMMING
    }

    public enum ParseStatus {
        SUCCESS,
        NEEDS_REVIEW,
        FAILED
    }

    public record ImportStructureRequest(
            @NotEmpty @Size(max = 20) List<@Valid SectionSpec> sections
    ) {
    }

    public record SectionSpec(
            @NotNull ImportQuestionType questionType,
            @Min(1) @Max(200) int questionCount,
            List<@Positive Long> questionIds
    ) {
    }

    public record ImportIssue(
            String code,
            String message,
            ImportQuestionType questionType,
            Integer questionNo
    ) {
    }

    public record StudentIdentity(String studentNo, String studentName, String filename) {
    }

    public record ParsedAnswer(
            Long questionId,
            ImportQuestionType questionType,
            int questionNo,
            String rawAnswer,
            Integer sourceStartBlock,
            Integer sourceEndBlock,
            ParseStatus parseStatus,
            List<ImportIssue> issues
    ) {
    }

    public record UnassignedTextRange(
            int sourceStartBlock,
            int sourceEndBlock,
            String rawText,
            String reason
    ) {
    }

    public record AnswerImportPreview(
            String studentNo,
            String studentName,
            String originalFilename,
            int expectedQuestionCount,
            int recognizedQuestionCount,
            int sourceBlockCount,
            ParseStatus parseStatus,
            List<ParsedAnswer> answers,
            List<UnassignedTextRange> unassignedTextRanges,
            List<ImportIssue> issues
    ) {
    }
}
