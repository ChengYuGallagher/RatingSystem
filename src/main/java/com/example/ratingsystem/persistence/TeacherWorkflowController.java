package com.example.ratingsystem.persistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.ratingsystem.persistence.GradingTaskDtos.GradingTaskView;
import static com.example.ratingsystem.persistence.TeacherDtos.ClassResultsView;
import static com.example.ratingsystem.persistence.TeacherDtos.SubmissionListItemView;

@RestController
@RequestMapping("/api")
public class TeacherWorkflowController {

    private final TeacherWorkflowService workflowService;
    private final BatchGradingTaskService taskService;

    public TeacherWorkflowController(TeacherWorkflowService workflowService,
                                     BatchGradingTaskService taskService) {
        this.workflowService = workflowService;
        this.taskService = taskService;
    }

    @GetMapping("/exams/{examId}/submissions")
    List<SubmissionListItemView> listSubmissions(@PathVariable Long examId) {
        return workflowService.listSubmissions(examId);
    }

    @GetMapping("/exams/{examId}/class-results")
    ClassResultsView classResults(@PathVariable Long examId) {
        return workflowService.getClassResults(examId);
    }

    @PostMapping("/exams/{examId}/grading-tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GradingTaskView startGrading(@PathVariable Long examId) {
        return taskService.start(examId);
    }

    @GetMapping("/exams/{examId}/grading-tasks/latest")
    GradingTaskView getLatestTask(@PathVariable Long examId) {
        return taskService.getLatest(examId);
    }

    @GetMapping("/grading/tasks/{taskId}")
    GradingTaskView getTask(@PathVariable Long taskId) {
        return taskService.get(taskId);
    }

    @PostMapping("/grading/tasks/{taskId}/retry-failed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GradingTaskView retryFailed(@PathVariable Long taskId) {
        return taskService.retryFailed(taskId);
    }
}
