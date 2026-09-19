package com.example.ratingsystem.batchimport;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface BatchImportJpaRepository extends JpaRepository<BatchImportEntity, Long> {

    Optional<BatchImportEntity> findFirstByExamIdOrderByIdDesc(Long examId);

    @Query("select distinct b from BatchImportEntity b where b.id = :id")
    Optional<BatchImportEntity> findDetailedById(@Param("id") Long id);
}

interface BatchImportStudentJpaRepository extends JpaRepository<BatchImportStudentEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"batch", "answers"})
    @Query("select s from BatchImportStudentEntity s where s.id = :id and s.batch.id = :batchId")
    Optional<BatchImportStudentEntity> findForUpdate(@Param("batchId") Long batchId, @Param("id") Long id);

    List<BatchImportStudentEntity> findByBatchIdAndReviewStatusOrderById(Long batchId, BatchReviewStatus status);

    boolean existsByBatchIdAndStudentNoAndReviewStatusInAndIdNot(
            Long batchId, String studentNo, List<BatchReviewStatus> statuses, Long id);
}

interface BatchImportIssueJpaRepository extends JpaRepository<BatchImportIssueEntity, Long> {
}
