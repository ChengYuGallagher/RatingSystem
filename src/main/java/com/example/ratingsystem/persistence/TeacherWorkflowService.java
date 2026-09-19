package com.example.ratingsystem.persistence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.ratingsystem.persistence.PersistenceDtos.GradingResultView;
import static com.example.ratingsystem.persistence.TeacherDtos.ClassQuestionView;
import static com.example.ratingsystem.persistence.TeacherDtos.ClassResultsView;
import static com.example.ratingsystem.persistence.TeacherDtos.ClassSubmissionView;
import static com.example.ratingsystem.persistence.TeacherDtos.QuestionScoreView;
import static com.example.ratingsystem.persistence.TeacherDtos.SubmissionListItemView;

@Service
public class TeacherWorkflowService {

    private final ExamPersistenceService examService;
    private final ExamSubmissionJpaRepository submissionRepository;
    private final PersistedGradingService gradingService;
    private final SubmissionScoreSummaryService summaryService;

    public TeacherWorkflowService(ExamPersistenceService examService,
                                  ExamSubmissionJpaRepository submissionRepository,
                                  PersistedGradingService gradingService,
                                  SubmissionScoreSummaryService summaryService) {
        this.examService = examService;
        this.submissionRepository = submissionRepository;
        this.gradingService = gradingService;
        this.summaryService = summaryService;
    }

    @Transactional(readOnly = true)
    public List<SubmissionListItemView> listSubmissions(Long examId) {
        examService.getExam(examId);
        return submissionRepository.findByExamIdOrderById(examId).stream().map(submission ->
                new SubmissionListItemView(
                        submission.getId(), submission.getStudent().getId(),
                        submission.getStudent().getStudentNo(), submission.getStudent().getName(),
                        submission.getAnswers().size()
                )).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ClassResultsView getClassResults(Long examId) {
        PersistenceDtos.ExamView exam = examService.getExam(examId);
        List<ClassQuestionView> questions = exam.questions().stream()
                .sorted(Comparator.comparingInt(PersistenceDtos.QuestionView::questionNo))
                .map(question -> new ClassQuestionView(
                        question.id(), question.questionNo(), question.questionType(), question.maxScore()))
                .toList();
        List<ClassSubmissionView> rows = submissionRepository.findByExamIdOrderById(examId).stream()
                .map(submission -> buildSubmissionRow(submission, questions)).toList();
        return new ClassResultsView(exam.id(), exam.name(), exam.standardsReviewed(), questions, rows);
    }

    private ClassSubmissionView buildSubmissionRow(ExamSubmissionEntity submission,
                                                    List<ClassQuestionView> questions) {
        PersistenceDtos.SubmissionScoreSummaryView summary = summaryService.getSummary(submission.getId());
        Map<Long, GradingResultView> results = gradingService.getResults(submission.getId()).stream()
                .collect(Collectors.toMap(GradingResultView::questionId, Function.identity()));
        List<QuestionScoreView> scores = questions.stream().map(question -> {
            GradingResultView result = results.get(question.questionId());
            return result == null
                    ? new QuestionScoreView(question.questionId(), question.questionNo(), null, null,
                    null, null, null)
                    : new QuestionScoreView(question.questionId(), question.questionNo(), result.gradingStatus(),
                    result.reviewStatus(), result.suggestedScore(), result.actualScore(), result.failureMessage());
        }).toList();
        return new ClassSubmissionView(
                submission.getId(), submission.getStudent().getId(), submission.getStudent().getStudentNo(),
                submission.getStudent().getName(), summary.completionStatus(), summary.confirmedQuestionCount(),
                summary.questionCount(), summary.confirmedScore(), summary.finalScore(), scores
        );
    }
}
