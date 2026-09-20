package com.example.ratingsystem.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamDeletionService {

    private final ExamJpaRepository examRepository;
    private final ExamCascadeDeletionRepository deletionRepository;

    public ExamDeletionService(ExamJpaRepository examRepository,
                               ExamCascadeDeletionRepository deletionRepository) {
        this.examRepository = examRepository;
        this.deletionRepository = deletionRepository;
    }

    @Transactional
    public void deleteExam(Long examId) {
        examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> new PersistenceNotFoundException("考试不存在: " + examId));
        try {
            if (deletionRepository.deleteExamGraph(examId) != 1) {
                throw new PersistenceConflictException("试卷删除失败，请刷新试卷库后重试");
            }
        } catch (DataAccessException exception) {
            throw new PersistenceConflictException("试卷删除失败，请确认没有正在执行的操作后重试");
        }
    }
}
