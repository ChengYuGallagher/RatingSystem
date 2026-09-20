package com.example.ratingsystem.batchimport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchImportQueryService {

    private final BatchImportJpaRepository repository;

    public BatchImportQueryService(BatchImportJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean existsForExam(Long examId) {
        return repository.existsByExamId(examId);
    }
}
