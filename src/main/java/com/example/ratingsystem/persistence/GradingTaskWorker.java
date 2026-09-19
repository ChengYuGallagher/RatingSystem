package com.example.ratingsystem.persistence;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
class GradingTaskWorker {

    private final GradingTaskItemJpaRepository itemRepository;
    private final GradingTaskJpaRepository taskRepository;
    private final PersistedGradingService gradingService;
    private final TransactionTemplate transactions;

    GradingTaskWorker(GradingTaskItemJpaRepository itemRepository,
                      GradingTaskJpaRepository taskRepository,
                      PersistedGradingService gradingService,
                      PlatformTransactionManager transactionManager) {
        this.itemRepository = itemRepository;
        this.taskRepository = taskRepository;
        this.gradingService = gradingService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Async("gradingTaskExecutor")
    public void run(Long taskId) {
        List<Long> itemIds = transactions.execute(status -> itemRepository
                .findByTaskIdAndStatusOrderById(taskId, GradingTaskItemStatus.PENDING)
                .stream().map(GradingTaskItemEntity::getId).toList());
        if (itemIds == null) {
            return;
        }
        for (Long itemId : itemIds) {
            processOne(itemId);
        }
        refreshTask(taskId);
    }

    private void processOne(Long itemId) {
        Long submissionId = transactions.execute(status -> {
            GradingTaskItemEntity item = itemRepository.findByIdForUpdate(itemId).orElse(null);
            if (item == null || item.getStatus() != GradingTaskItemStatus.PENDING) {
                return null;
            }
            item.start();
            return item.getSubmission().getId();
        });
        if (submissionId == null) {
            return;
        }
        try {
            gradingService.gradeSubmission(submissionId);
            finish(itemId, null);
        } catch (RuntimeException exception) {
            finish(itemId, safeMessage(exception));
        }
    }

    private void finish(Long itemId, String errorMessage) {
        transactions.executeWithoutResult(status -> {
            GradingTaskItemEntity item = itemRepository.findByIdForUpdate(itemId)
                    .orElseThrow(() -> new PersistenceNotFoundException("批量评分子任务不存在: " + itemId));
            if (errorMessage == null) {
                item.succeed();
            } else {
                item.fail(errorMessage);
            }
            item.getTask().refreshCounts();
        });
    }

    private void refreshTask(Long taskId) {
        transactions.executeWithoutResult(status -> taskRepository.findByIdForUpdate(taskId)
                .ifPresent(GradingTaskEntity::refreshCounts));
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "该答卷评分失败" : message.substring(0, Math.min(1000, message.length()));
    }
}
