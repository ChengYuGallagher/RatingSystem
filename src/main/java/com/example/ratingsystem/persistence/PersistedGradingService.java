package com.example.ratingsystem.persistence;

import com.example.ratingsystem.grading.model.CriterionScore;
import com.example.ratingsystem.grading.model.GradingRequest;
import com.example.ratingsystem.grading.model.GradingResult;
import com.example.ratingsystem.grading.model.RubricItem;
import com.example.ratingsystem.grading.service.GradingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.ratingsystem.persistence.PersistenceDtos.CriterionResultView;
import static com.example.ratingsystem.persistence.PersistenceDtos.GradeSubmissionView;
import static com.example.ratingsystem.persistence.PersistenceDtos.GradingResultView;
import static com.example.ratingsystem.persistence.PersistenceDtos.ReviewAction;
import static com.example.ratingsystem.persistence.PersistenceDtos.ReviewRequest;
import static com.example.ratingsystem.persistence.PersistenceDtos.RubricItemView;

@Service
public class PersistedGradingService {

    private final GradingService gradingService;
    private final ExamJpaRepository examRepository;
    private final ExamSubmissionJpaRepository submissionRepository;
    private final StudentAnswerJpaRepository answerRepository;
    private final GradingResultJpaRepository resultRepository;
    private final GradingPersistenceProperties properties;
    private final TransactionTemplate transactions;

    public PersistedGradingService(
            GradingService gradingService,
            ExamJpaRepository examRepository,
            ExamSubmissionJpaRepository submissionRepository,
            StudentAnswerJpaRepository answerRepository,
            GradingResultJpaRepository resultRepository,
            GradingPersistenceProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.gradingService = gradingService;
        this.examRepository = examRepository;
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public GradeSubmissionView gradeSubmission(Long submissionId) {
        List<Long> answerIds = transactions.execute(status -> {
            ExamSubmissionEntity submission = submissionRepository.findDetailedById(submissionId)
                    .orElseThrow(() -> new PersistenceNotFoundException("答卷不存在: " + submissionId));
            ExamEntity exam = examRepository.findByIdForUpdate(submission.getExam().getId())
                    .orElseThrow(() -> new PersistenceNotFoundException("考试不存在"));
            exam.startScoring();
            return submission.getAnswers().stream()
                    .sorted(Comparator.comparingInt(answer -> answer.getQuestion().getQuestionNo()))
                    .map(StudentAnswerEntity::getId)
                    .toList();
        });
        if (answerIds == null) {
            throw new IllegalStateException("无法读取答卷答案");
        }
        answerIds.forEach(answerId -> gradeAnswer(answerId, false));
        return new GradeSubmissionView(submissionId, getResults(submissionId));
    }

    public GradingResultView retry(Long resultId) {
        GradingWork work = claimByResultId(resultId);
        if (work == null) {
            throw new IllegalStateException("无法创建重试任务");
        }
        execute(work);
        return getResult(resultId);
    }

    public GradingResultView review(Long resultId, ReviewRequest request) {
        return transactions.execute(status -> {
            GradingResultEntity result = resultRepository.findByIdForUpdate(resultId)
                    .orElseThrow(() -> new PersistenceNotFoundException("评分结果不存在: " + resultId));
            if (result.getVersion() != request.expectedVersion()) {
                throw new PersistenceConflictException("评分结果已被其他审核操作修改，请重新查询后再提交");
            }
            if (result.getGradingStatus() != GradingStatus.SUCCESS) {
                throw new PersistenceConflictException("只有自动评分成功的结果才能进行人工审核");
            }

            BigDecimal actualScore = resolveActualScore(result, request);
            validateActualScore(actualScore, result.getStudentAnswer().getQuestion().getMaxScore());
            result.confirm(actualScore);
            resultRepository.flush();
            return toView(result);
        });
    }

    public List<GradingResultView> getResults(Long submissionId) {
        return transactions.execute(status -> {
            if (!submissionRepository.existsById(submissionId)) {
                throw new PersistenceNotFoundException("答卷不存在: " + submissionId);
            }
            Instant staleBefore = staleBefore();
            List<GradingResultEntity> results = resultRepository.findByStudentAnswerSubmissionId(submissionId);
            results.stream()
                    .filter(result -> isStale(result, staleBefore))
                    .forEach(result -> result.fail("检测到上次评分进程异常中断，请使用显式重试接口"));
            return results.stream()
                    .sorted(Comparator.comparingInt(result -> result.getStudentAnswer().getQuestion().getQuestionNo()))
                    .map(this::toView)
                    .toList();
        });
    }

    private void gradeAnswer(Long answerId, boolean retry) {
        GradingWork work = claimByAnswerId(answerId, retry);
        if (work != null) {
            execute(work);
        }
    }

    private GradingWork claimByAnswerId(Long answerId, boolean retry) {
        return transactions.execute(status -> {
            StudentAnswerEntity answer = answerRepository.findByIdForUpdate(answerId)
                    .orElseThrow(() -> new PersistenceNotFoundException("学生答案不存在: " + answerId));
            GradingResultEntity existing = resultRepository.findByStudentAnswerId(answerId).orElse(null);
            if (existing != null) {
                if (!retry) {
                    if (isStale(existing, staleBefore())) {
                        existing.fail("检测到上次评分进程异常中断，请使用显式重试接口");
                    }
                    return null;
                }
                prepareRetry(existing);
                return buildWork(answer, existing.getId());
            }
            GradingResultEntity created = resultRepository.saveAndFlush(GradingResultEntity.running(answer, Instant.now()));
            return buildWork(answer, created.getId());
        });
    }

    private GradingWork claimByResultId(Long resultId) {
        return transactions.execute(status -> {
            GradingResultEntity result = resultRepository.findByIdForUpdate(resultId)
                    .orElseThrow(() -> new PersistenceNotFoundException("评分结果不存在: " + resultId));
            prepareRetry(result);
            return buildWork(result.getStudentAnswer(), result.getId());
        });
    }

    private void prepareRetry(GradingResultEntity result) {
        if (result.getReviewStatus() == PersistentReviewStatus.CONFIRMED) {
            throw new PersistenceConflictException("已人工确认的评分结果不能重新自动评分");
        }
        if (result.getGradingStatus() == GradingStatus.SUCCESS) {
            throw new PersistenceConflictException("自动评分已成功，重复请求应直接使用已有结果");
        }
        if (result.getGradingStatus() == GradingStatus.RUNNING && !isStale(result, staleBefore())) {
            throw new PersistenceConflictException("评分正在进行中，不能重复发起");
        }
        result.restart(Instant.now());
    }

    private GradingWork buildWork(StudentAnswerEntity answer, Long resultId) {
        QuestionEntity question = answer.getQuestion();
        List<RubricItem> rubricItems = question.getRubricItems().stream()
                .map(item -> new RubricItem(item.getId(), item.getName(), item.getMaxScore()))
                .toList();
        return new GradingWork(resultId, new GradingRequest(
                question.getId(), question.getQuestionType(), question.getContent(), question.getMaxScore(),
                question.getReferenceAnswer(), question.getGradingCriteria(), answer.getAnswerText(),
                question.getFillBlankGradingMode(), rubricItems
        ));
    }

    private void execute(GradingWork work) {
        GradingResult outcome;
        try {
            outcome = gradingService.grade(work.request());
        } catch (RuntimeException exception) {
            markFailed(work.resultId(), "评分过程发生异常，未生成建议分数");
            return;
        }

        try {
            transactions.executeWithoutResult(status -> {
                GradingResultEntity result = resultRepository.findByIdForUpdate(work.resultId())
                        .orElseThrow(() -> new PersistenceNotFoundException("评分结果不存在: " + work.resultId()));
                if (result.getGradingStatus() != GradingStatus.RUNNING) {
                    throw new PersistenceConflictException("评分状态已发生变化，拒绝覆盖现有结果");
                }
                if (outcome.gradingStatus() == com.example.ratingsystem.grading.model.GradingStatus.FAILED) {
                    result.fail(outcome.failureMessage());
                    return;
                }

                Map<Long, QuestionRubricItemEntity> rubrics = result.getStudentAnswer().getQuestion().getRubricItems()
                        .stream().collect(Collectors.toMap(QuestionRubricItemEntity::getId, Function.identity()));
                result.succeed(outcome.suggestedScore(), outcome.reason());
                for (CriterionScore item : outcome.criterionScores()) {
                    QuestionRubricItemEntity rubric = rubrics.get(item.rubricItemId());
                    if (rubric == null) {
                        throw new PersistenceValidationException("评分结果包含未知 rubricItemId");
                    }
                    result.addItem(new GradingResultItemEntity(
                            rubric, item.maxScore(), item.suggestedScore(), item.reason()
                    ));
                }
            });
        } catch (RuntimeException exception) {
            markFailed(work.resultId(), "评分结果保存失败，未生成建议分数");
        }
    }

    private void markFailed(Long resultId, String message) {
        transactions.executeWithoutResult(status -> resultRepository.findByIdForUpdate(resultId)
                .ifPresent(result -> {
                    if (result.getGradingStatus() == GradingStatus.RUNNING) {
                        result.fail(message);
                    }
                }));
    }

    private GradingResultView getResult(Long resultId) {
        return transactions.execute(status -> resultRepository.findByIdForUpdate(resultId)
                .map(this::toView)
                .orElseThrow(() -> new PersistenceNotFoundException("评分结果不存在: " + resultId)));
    }

    private GradingResultView toView(GradingResultEntity result) {
        StudentAnswerEntity answer = result.getStudentAnswer();
        QuestionEntity question = answer.getQuestion();
        return new GradingResultView(
                result.getId(), answer.getId(), question.getId(), question.getQuestionNo(), question.getQuestionType(),
                question.getContent(), question.getMaxScore(), question.getReferenceAnswer(), question.getGradingCriteria(),
                question.getRubricItems().stream().map(item -> new RubricItemView(
                        item.getId(), item.getItemOrder(), item.getName(), item.getMaxScore()
                )).toList(),
                answer.getAnswerText(), result.getGradingStatus(), result.getSuggestedScore(), result.getReason(),
                result.getFailureMessage(), result.getActualScore(), result.getReviewStatus(), result.getAttemptCount(),
                result.getVersion(),
                result.getItems().stream().map(item -> new CriterionResultView(
                        item.getRubricItem().getId(), item.getRubricItem().getName(), item.getMaxScore(),
                        item.getSuggestedScore(), item.getReason()
                )).toList()
        );
    }

    private BigDecimal resolveActualScore(GradingResultEntity result, ReviewRequest request) {
        if (request.action() == ReviewAction.ACCEPT_SUGGESTION) {
            if (request.actualScore() != null) {
                throw new PersistenceValidationException("接受建议分时不能同时提供 actualScore");
            }
            if (result.getSuggestedScore() == null) {
                throw new PersistenceConflictException("当前评分结果没有可接受的建议分数");
            }
            return result.getSuggestedScore();
        }
        if (request.action() == ReviewAction.SET_SCORE) {
            if (request.actualScore() == null) {
                throw new PersistenceValidationException("手动评分必须提供 actualScore");
            }
            return request.actualScore();
        }
        throw new PersistenceValidationException("不支持的审核操作");
    }

    private void validateActualScore(BigDecimal actualScore, BigDecimal maxScore) {
        if (actualScore.scale() > 2) {
            throw new PersistenceValidationException("实际分数最多保留两位小数");
        }
        if (actualScore.signum() < 0 || actualScore.compareTo(maxScore) > 0) {
            throw new PersistenceValidationException("实际分数必须在 0 到题目满分之间");
        }
    }

    private Instant staleBefore() {
        return Instant.now().minus(properties.runningTimeout());
    }

    private boolean isStale(GradingResultEntity result, Instant staleBefore) {
        return result.getGradingStatus() == GradingStatus.RUNNING
                && result.getRunningSince() != null
                && result.getRunningSince().isBefore(staleBefore);
    }

    private record GradingWork(Long resultId, GradingRequest request) {
    }
}
