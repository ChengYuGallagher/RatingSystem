package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.persistence.ExamPersistenceService;
import com.example.ratingsystem.persistence.PersistenceConflictException;
import com.example.ratingsystem.persistence.PersistenceDtos.AnswerInput;
import com.example.ratingsystem.persistence.PersistenceDtos.CreateSubmissionRequest;
import com.example.ratingsystem.persistence.PersistenceDtos.SubmissionView;
import com.example.ratingsystem.persistence.PersistenceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class BatchSubmissionImporter {

    private final BatchImportJpaRepository batchRepository;
    private final BatchImportStudentJpaRepository studentRepository;
    private final BatchImportIssueJpaRepository issueRepository;
    private final ExamPersistenceService examService;

    BatchSubmissionImporter(BatchImportJpaRepository batchRepository,
                            BatchImportStudentJpaRepository studentRepository,
                            BatchImportIssueJpaRepository issueRepository,
                            ExamPersistenceService examService) {
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.issueRepository = issueRepository;
        this.examService = examService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    SubmissionView importStudent(Long batchId, Long studentImportId) {
        BatchImportStudentEntity student = studentRepository.findForUpdate(batchId, studentImportId)
                .orElseThrow(() -> new PersistenceNotFoundException("批量导入学生记录不存在: " + studentImportId));
        if (student.getReviewStatus() == BatchReviewStatus.IMPORTED) {
            throw new PersistenceConflictException("该学生答卷已经导入");
        }
        if (student.getReviewStatus() != BatchReviewStatus.CONFIRMED) {
            throw new PersistenceConflictException("只有教师已确认的解析结果才能正式导入");
        }
        CreateSubmissionRequest request = new CreateSubmissionRequest(
                student.getStudentNo(), student.getStudentName(), student.getAnswers().stream()
                .map(answer -> new AnswerInput(answer.getQuestionId(), answer.getRawAnswer()))
                .toList()
        );
        SubmissionView submission = examService.createSubmission(student.getBatch().getExamId(), request);
        student.imported(submission.id());
        return submission;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(Long batchId, Long studentImportId, String message) {
        BatchImportEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new PersistenceNotFoundException("批量导入任务不存在: " + batchId));
        BatchImportStudentEntity student = studentRepository.findById(studentImportId)
                .orElseThrow(() -> new PersistenceNotFoundException("批量导入学生记录不存在: " + studentImportId));
        BatchImportIssueEntity issue = new BatchImportIssueEntity(
                student, null, "FORMAL_IMPORT_FAILED", message);
        issue.setBatch(batch);
        issueRepository.save(issue);
    }
}
