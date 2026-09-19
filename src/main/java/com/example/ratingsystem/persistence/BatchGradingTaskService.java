package com.example.ratingsystem.persistence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.example.ratingsystem.persistence.GradingTaskDtos.GradingTaskItemView;
import static com.example.ratingsystem.persistence.GradingTaskDtos.GradingTaskView;

@Service
public class BatchGradingTaskService {

    private final ExamJpaRepository examRepository;
    private final ExamSubmissionJpaRepository submissionRepository;
    private final GradingTaskJpaRepository taskRepository;
    private final GradingTaskItemJpaRepository itemRepository;
    private final GradingTaskWorker worker;
    private final GradingPersistenceProperties properties;
    private final TransactionTemplate transactions;

    public BatchGradingTaskService(ExamJpaRepository examRepository,
                                   ExamSubmissionJpaRepository submissionRepository,
                                   GradingTaskJpaRepository taskRepository,
                                   GradingTaskItemJpaRepository itemRepository,
                                   GradingTaskWorker worker,
                                   GradingPersistenceProperties properties,
                                   PlatformTransactionManager transactionManager) {
        this.examRepository = examRepository;
        this.submissionRepository = submissionRepository;
        this.taskRepository = taskRepository;
        this.itemRepository = itemRepository;
        this.worker = worker;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public GradingTaskView start(Long examId) {
        TaskStart start = transactions.execute(status -> createOrReuse(examId));
        if (start == null) {
            throw new IllegalStateException("无法创建批量评分任务");
        }
        if (start.shouldRun()) {
            worker.run(start.taskId());
        }
        return get(start.taskId());
    }

    public GradingTaskView retryFailed(Long taskId) {
        boolean shouldRun = Boolean.TRUE.equals(transactions.execute(status -> {
            GradingTaskEntity task = taskRepository.findByIdForUpdate(taskId)
                    .orElseThrow(() -> new PersistenceNotFoundException("批量评分任务不存在: " + taskId));
            List<GradingTaskItemEntity> failed = itemRepository
                    .findByTaskIdAndStatusOrderById(taskId, GradingTaskItemStatus.FAILED);
            if (failed.isEmpty()) {
                throw new PersistenceConflictException("当前任务没有可重试的失败答卷");
            }
            failed.forEach(GradingTaskItemEntity::prepareRetry);
            task.restart(Instant.now());
            return true;
        }));
        if (shouldRun) {
            worker.run(taskId);
        }
        return get(taskId);
    }

    public GradingTaskView get(Long taskId) {
        return transactions.execute(status -> taskRepository.findDetailedById(taskId)
                .map(this::toView)
                .orElseThrow(() -> new PersistenceNotFoundException("批量评分任务不存在: " + taskId)));
    }

    public GradingTaskView getLatest(Long examId) {
        return transactions.execute(status -> {
            if (!examRepository.existsById(examId)) {
                throw new PersistenceNotFoundException("考试不存在: " + examId);
            }
            GradingTaskEntity latest = taskRepository.findFirstByExamIdOrderByIdDesc(examId)
                    .orElseThrow(() -> new PersistenceNotFoundException("该考试尚无批量评分任务"));
            return taskRepository.findDetailedById(latest.getId())
                    .map(this::toView)
                    .orElseThrow(() -> new PersistenceNotFoundException("批量评分任务不存在: " + latest.getId()));
        });
    }

    private TaskStart createOrReuse(Long examId) {
        ExamEntity exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> new PersistenceNotFoundException("考试不存在: " + examId));
        if (!exam.isStandardsReviewed()) {
            throw new PersistenceConflictException("必须先由教师核对并确认标准答案和评分细则");
        }
        List<ExamSubmissionEntity> submissions = submissionRepository.findByExamIdOrderById(examId);
        if (submissions.isEmpty()) {
            throw new PersistenceValidationException("本次考试尚无已导入答卷");
        }
        GradingTaskEntity latest = taskRepository.findFirstByExamIdOrderByIdDesc(examId).orElse(null);
        if (latest != null && latest.getStatus() == GradingTaskStatus.RUNNING) {
            if (isInterrupted(latest)) {
                latest.getItems().forEach(GradingTaskItemEntity::recoverInterruptedRun);
                latest.restart(Instant.now());
                return new TaskStart(latest.getId(), true);
            }
            return new TaskStart(latest.getId(), false);
        }
        if (latest != null) {
            Set<Long> previous = latest.getItems().stream()
                    .map(item -> item.getSubmission().getId()).collect(java.util.stream.Collectors.toSet());
            Set<Long> current = submissions.stream().map(ExamSubmissionEntity::getId).collect(java.util.stream.Collectors.toSet());
            if (previous.equals(current)) {
                return new TaskStart(latest.getId(), false);
            }
        }
        GradingTaskEntity task = new GradingTaskEntity(exam, Instant.now());
        submissions.forEach(submission -> task.addItem(new GradingTaskItemEntity(submission)));
        taskRepository.saveAndFlush(task);
        return new TaskStart(task.getId(), true);
    }

    private boolean isInterrupted(GradingTaskEntity task) {
        Instant staleBefore = Instant.now().minus(properties.runningTimeout());
        boolean staleStartedTask = task.getStartedAt() != null && task.getStartedAt().isBefore(staleBefore);
        boolean hasFreshRunningItem = task.getItems().stream()
                .filter(item -> item.getStatus() == GradingTaskItemStatus.RUNNING)
                .anyMatch(item -> item.getUpdatedAt() != null && !item.getUpdatedAt().isBefore(staleBefore));
        return staleStartedTask && !hasFreshRunningItem;
    }

    private GradingTaskView toView(GradingTaskEntity task) {
        return new GradingTaskView(
                task.getId(), task.getExam().getId(), task.getStatus(), task.getTotalCount(),
                task.getProcessedCount(), task.getSuccessCount(), task.getFailedCount(),
                task.getCreatedAt(), task.getStartedAt(), task.getCompletedAt(),
                task.getItems().stream().map(item -> new GradingTaskItemView(
                        item.getId(), item.getSubmission().getId(), item.getSubmission().getStudent().getStudentNo(),
                        item.getSubmission().getStudent().getName(), item.getStatus(), item.getAttemptCount(),
                        item.getErrorMessage()
                )).toList()
        );
    }

    private record TaskStart(Long taskId, boolean shouldRun) {
    }
}
