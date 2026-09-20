package com.example.ratingsystem.persistence;

import com.example.ratingsystem.batchimport.BatchImportQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamDeletionService {

    private final ExamJpaRepository examRepository;
    private final ExamSubmissionJpaRepository submissionRepository;
    private final GradingResultJpaRepository resultRepository;
    private final GradingTaskJpaRepository taskRepository;
    private final BatchImportQueryService importQueryService;

    public ExamDeletionService(ExamJpaRepository examRepository,
                               ExamSubmissionJpaRepository submissionRepository,
                               GradingResultJpaRepository resultRepository,
                               GradingTaskJpaRepository taskRepository,
                               BatchImportQueryService importQueryService) {
        this.examRepository = examRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.taskRepository = taskRepository;
        this.importQueryService = importQueryService;
    }

    @Transactional
    public void deleteEmptyExam(Long examId) {
        ExamEntity exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> new PersistenceNotFoundException("考试不存在: " + examId));
        if (submissionRepository.existsByExamId(examId)
                || resultRepository.countByExamId(examId) > 0
                || taskRepository.existsByExamId(examId)) {
            throw new PersistenceConflictException("该试卷已有答卷、评分任务或成绩，不能直接删除");
        }
        if (importQueryService.existsForExam(examId)) {
            throw new PersistenceConflictException("该试卷已有答卷导入记录，不能直接删除");
        }
        examRepository.delete(exam);
        examRepository.flush();
    }
}
