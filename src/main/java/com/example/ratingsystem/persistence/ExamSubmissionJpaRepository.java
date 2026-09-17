package com.example.ratingsystem.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ExamSubmissionJpaRepository extends JpaRepository<ExamSubmissionEntity, Long> {

    boolean existsByExamIdAndStudentId(Long examId, Long studentId);

    @EntityGraph(attributePaths = {
            "exam", "student", "answers", "answers.question", "answers.question.rubricItems"
    })
    Optional<ExamSubmissionEntity> findDetailedById(Long id);
}
