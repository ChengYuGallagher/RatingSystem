package com.example.ratingsystem.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface GradingResultJpaRepository extends JpaRepository<GradingResultEntity, Long> {

    @Query("select count(r) from GradingResultEntity r where r.studentAnswer.submission.exam.id = :examId")
    long countByExamId(@Param("examId") Long examId);

    Optional<GradingResultEntity> findByStudentAnswerId(Long studentAnswerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"studentAnswer", "studentAnswer.question", "studentAnswer.question.rubricItems", "items", "items.rubricItem"})
    @Query("select r from GradingResultEntity r where r.id = :id")
    Optional<GradingResultEntity> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"studentAnswer", "studentAnswer.question", "studentAnswer.question.rubricItems", "items", "items.rubricItem"})
    List<GradingResultEntity> findByStudentAnswerSubmissionId(Long submissionId);
}
