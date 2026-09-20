package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.batchimport.BatchImportDtos.BatchImportExecutionView;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchImportView;
import com.example.ratingsystem.batchimport.BatchImportDtos.ConfirmImportAndGradeView;
import com.example.ratingsystem.persistence.BatchGradingTaskService;
import com.example.ratingsystem.persistence.ExamPersistenceService;
import com.example.ratingsystem.persistence.GradingTaskDtos.GradingTaskView;
import com.example.ratingsystem.persistence.PersistenceConflictException;
import org.springframework.stereotype.Service;

@Service
public class BatchImportGradingWorkflowService {

    private final BatchAnswerImportService importService;
    private final BatchGradingTaskService gradingTaskService;
    private final ExamPersistenceService examService;

    public BatchImportGradingWorkflowService(BatchAnswerImportService importService,
                                             BatchGradingTaskService gradingTaskService,
                                             ExamPersistenceService examService) {
        this.importService = importService;
        this.gradingTaskService = gradingTaskService;
        this.examService = examService;
    }

    public ConfirmImportAndGradeView confirmImportAndGrade(Long batchId) {
        BatchImportView before = importService.getBatch(batchId);
        if (!examService.getExam(before.examId()).standardsReviewed()) {
            throw new PersistenceConflictException("必须先由教师核对并确认评分标准，才能导入并开始评分");
        }
        int automaticallyConfirmed = importService.confirmAllReady(batchId);
        BatchImportExecutionView imported = importService.importConfirmed(batchId);
        GradingTaskView task = gradingTaskService.start(before.examId());
        String stage = imported.failures().isEmpty() ? "GRADING_STARTED" : "GRADING_STARTED_WITH_IMPORT_FAILURES";
        return new ConfirmImportAndGradeView(
                batchId, before.examId(), automaticallyConfirmed, imported, task, stage);
    }
}
