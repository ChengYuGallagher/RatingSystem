package com.example.ratingsystem.persistence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.example.ratingsystem.persistence.PersistenceDtos.ScoreSummaryStatus;
import static com.example.ratingsystem.persistence.PersistenceDtos.SubmissionScoreSummaryView;

@Service
public class SubmissionScoreSummaryService {

    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2);

    private final ExamSubmissionJpaRepository submissionRepository;
    private final GradingResultJpaRepository resultRepository;

    public SubmissionScoreSummaryService(ExamSubmissionJpaRepository submissionRepository,
                                         GradingResultJpaRepository resultRepository) {
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SubmissionScoreSummaryView getSummary(Long submissionId) {
        ExamSubmissionEntity submission = submissionRepository.findDetailedById(submissionId)
                .orElseThrow(() -> new PersistenceNotFoundException("答卷不存在: " + submissionId));
        ExamEntity exam = submission.getExam();
        List<QuestionEntity> questions = exam.getQuestions();

        Map<Long, QuestionEntity> questionsById = new HashMap<>();
        boolean associationsValid = true;
        BigDecimal examMaxScore = ZERO_SCORE;
        for (QuestionEntity question : questions) {
            if (questionsById.putIfAbsent(question.getId(), question) != null) {
                associationsValid = false;
                continue;
            }
            examMaxScore = examMaxScore.add(question.getMaxScore());
        }

        Map<Long, StudentAnswerEntity> answersById = new HashMap<>();
        Map<Long, StudentAnswerEntity> answersByQuestionId = new HashMap<>();
        Set<Long> invalidQuestionLinks = new HashSet<>();
        for (StudentAnswerEntity answer : submission.getAnswers()) {
            if (answersById.putIfAbsent(answer.getId(), answer) != null) {
                continue;
            }
            Long questionId = answer.getQuestion().getId();
            if (!questionsById.containsKey(questionId)
                    || !Objects.equals(answer.getSubmission().getId(), submission.getId())) {
                associationsValid = false;
                continue;
            }
            if (answersByQuestionId.putIfAbsent(questionId, answer) != null) {
                associationsValid = false;
                invalidQuestionLinks.add(questionId);
            }
        }

        List<GradingResultEntity> results = resultRepository.findByStudentAnswerSubmissionId(submissionId);
        Map<Long, GradingResultEntity> resultsByAnswerId = new HashMap<>();
        Set<Long> resultIds = new HashSet<>();
        Set<Long> invalidAnswerLinks = new HashSet<>();
        for (GradingResultEntity result : results) {
            if (!resultIds.add(result.getId())) {
                continue;
            }
            StudentAnswerEntity answer = result.getStudentAnswer();
            if (answer == null || !answersById.containsKey(answer.getId())) {
                associationsValid = false;
                continue;
            }
            if (resultsByAnswerId.putIfAbsent(answer.getId(), result) != null) {
                associationsValid = false;
                invalidAnswerLinks.add(answer.getId());
                continue;
            }
        }

        BigDecimal confirmedScore = ZERO_SCORE;
        int confirmedQuestionCount = 0;
        int successfullyGradedQuestionCount = 0;
        boolean everyQuestionComplete = !questionsById.isEmpty();
        for (QuestionEntity question : questionsById.values()) {
            StudentAnswerEntity answer = answersByQuestionId.get(question.getId());
            if (answer == null || invalidQuestionLinks.contains(question.getId())) {
                everyQuestionComplete = false;
                continue;
            }
            GradingResultEntity result = resultsByAnswerId.get(answer.getId());
            if (result == null || invalidAnswerLinks.contains(answer.getId())
                    || !Objects.equals(result.getStudentAnswer().getQuestion().getId(), question.getId())) {
                everyQuestionComplete = false;
                continue;
            }
            if (result.getGradingStatus() == GradingStatus.SUCCESS) {
                successfullyGradedQuestionCount++;
            }
            if (!isLegallyConfirmed(result, question.getMaxScore())) {
                everyQuestionComplete = false;
                continue;
            }
            confirmedScore = confirmedScore.add(result.getActualScore());
            confirmedQuestionCount++;
        }

        int questionCount = questionsById.size();
        int answerRecordCount = answersById.size();
        int gradingResultCount = resultIds.size();
        boolean complete = everyQuestionComplete
                && associationsValid
                && answerRecordCount == questionCount
                && gradingResultCount == questionCount
                && confirmedQuestionCount == questionCount;
        ScoreSummaryStatus status = complete ? ScoreSummaryStatus.COMPLETE : ScoreSummaryStatus.INCOMPLETE;

        return new SubmissionScoreSummaryView(
                exam.getId(), exam.getName(), submission.getId(), submission.getStudent().getId(),
                submission.getStudent().getStudentNo(), submission.getStudent().getName(), examMaxScore,
                questionCount, answerRecordCount, gradingResultCount, successfullyGradedQuestionCount,
                confirmedQuestionCount, questionCount - confirmedQuestionCount, confirmedScore,
                status, complete ? confirmedScore : null
        );
    }

    private boolean isLegallyConfirmed(GradingResultEntity result, BigDecimal maxScore) {
        boolean gradingFinished = result.getGradingStatus() == GradingStatus.SUCCESS
                || result.getGradingStatus() == GradingStatus.FAILED;
        BigDecimal actualScore = result.getActualScore();
        return gradingFinished
                && result.getReviewStatus() == PersistentReviewStatus.CONFIRMED
                && actualScore != null
                && actualScore.scale() <= 2
                && actualScore.signum() >= 0
                && actualScore.compareTo(maxScore) <= 0;
    }
}
