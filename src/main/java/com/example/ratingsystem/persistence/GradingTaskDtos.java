package com.example.ratingsystem.persistence;

import java.time.Instant;
import java.util.List;

public final class GradingTaskDtos {

    private GradingTaskDtos() {
    }

    public record GradingTaskView(
            Long id,
            Long examId,
            GradingTaskStatus status,
            int totalCount,
            int processedCount,
            int successCount,
            int failedCount,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            List<GradingTaskItemView> items
    ) {
    }

    public record GradingTaskItemView(
            Long id,
            Long submissionId,
            String studentNo,
            String studentName,
            GradingTaskItemStatus status,
            int attemptCount,
            String errorMessage
    ) {
    }
}
