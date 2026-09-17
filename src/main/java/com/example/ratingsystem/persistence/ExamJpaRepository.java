package com.example.ratingsystem.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface ExamJpaRepository extends JpaRepository<ExamEntity, Long> {

    @EntityGraph(attributePaths = {"questions", "questions.rubricItems"})
    @Query("select e from ExamEntity e where e.id = :id")
    Optional<ExamEntity> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExamEntity e where e.id = :id")
    Optional<ExamEntity> findByIdForUpdate(@Param("id") Long id);
}
