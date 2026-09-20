package com.example.ratingsystem.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

interface ExamSubmissionJpaRepository extends JpaRepository<ExamSubmissionEntity, Long> {

    boolean existsByExamId(Long examId);

    boolean existsByExamIdAndStudentId(Long examId, Long studentId);

    @EntityGraph(attributePaths = {"exam", "student"})
    List<ExamSubmissionEntity> findByExamIdOrderById(Long examId);

    @EntityGraph(attributePaths = {
            "exam", "student", "answers", "answers.question", "answers.question.rubricItems"
    })
    Optional<ExamSubmissionEntity> findDetailedById(Long id);
}
