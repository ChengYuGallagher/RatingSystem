package com.example.ratingsystem.persistence;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.ratingsystem.persistence.PersistenceDtos.CreateExamRequest;
import static com.example.ratingsystem.persistence.PersistenceDtos.CreateSubmissionRequest;
import static com.example.ratingsystem.persistence.PersistenceDtos.ExamView;
import static com.example.ratingsystem.persistence.PersistenceDtos.GradeSubmissionView;
import static com.example.ratingsystem.persistence.PersistenceDtos.GradingResultView;
import static com.example.ratingsystem.persistence.PersistenceDtos.ReviewRequest;
import static com.example.ratingsystem.persistence.PersistenceDtos.SubmissionScoreSummaryView;
import static com.example.ratingsystem.persistence.PersistenceDtos.SubmissionView;

@RestController
@RequestMapping("/api")
public class PersistenceController {

    private final ExamPersistenceService examService;
    private final PersistedGradingService gradingService;
    private final SubmissionScoreSummaryService summaryService;

    public PersistenceController(ExamPersistenceService examService, PersistedGradingService gradingService,
                                 SubmissionScoreSummaryService summaryService) {
        this.examService = examService;
        this.gradingService = gradingService;
        this.summaryService = summaryService;
    }

    @PostMapping("/exams")
    @ResponseStatus(HttpStatus.CREATED)
    ExamView createExam(@Valid @RequestBody CreateExamRequest request) {
        return examService.createExam(request);
    }

    @PostMapping("/exams/{examId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    SubmissionView createSubmission(@PathVariable Long examId,
                                    @Valid @RequestBody CreateSubmissionRequest request) {
        return examService.createSubmission(examId, request);
    }

    @PostMapping("/submissions/{submissionId}/grading")
    GradeSubmissionView gradeSubmission(@PathVariable Long submissionId) {
        return gradingService.gradeSubmission(submissionId);
    }

    @GetMapping("/submissions/{submissionId}/results")
    List<GradingResultView> getResults(@PathVariable Long submissionId) {
        return gradingService.getResults(submissionId);
    }

    @PostMapping("/grading/results/{resultId}/retry")
    GradingResultView retry(@PathVariable Long resultId) {
        return gradingService.retry(resultId);
    }

    @PutMapping("/grading/results/{resultId}/review")
    GradingResultView review(@PathVariable Long resultId, @Valid @RequestBody ReviewRequest request) {
        return gradingService.review(resultId, request);
    }

    @GetMapping("/submissions/{submissionId}/summary")
    SubmissionScoreSummaryView getSummary(@PathVariable Long submissionId) {
        return summaryService.getSummary(submissionId);
    }
}
