package com.example.ratingsystem.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface StudentAnswerJpaRepository extends JpaRepository<StudentAnswerEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"submission", "submission.exam", "question", "question.rubricItems"})
    @Query("select a from StudentAnswerEntity a where a.id = :id")
    Optional<StudentAnswerEntity> findByIdForUpdate(@Param("id") Long id);
}
