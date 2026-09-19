package com.example.ratingsystem.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface GradingTaskJpaRepository extends JpaRepository<GradingTaskEntity, Long> {

    Optional<GradingTaskEntity> findFirstByExamIdOrderByIdDesc(Long examId);

    @EntityGraph(attributePaths = {"exam", "items", "items.submission", "items.submission.student"})
    @Query("select t from GradingTaskEntity t where t.id = :id")
    Optional<GradingTaskEntity> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from GradingTaskEntity t where t.id = :id")
    Optional<GradingTaskEntity> findByIdForUpdate(@Param("id") Long id);
}

interface GradingTaskItemJpaRepository extends JpaRepository<GradingTaskItemEntity, Long> {

    List<GradingTaskItemEntity> findByTaskIdAndStatusOrderById(Long taskId, GradingTaskItemStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"task", "task.items", "submission", "submission.student"})
    @Query("select i from GradingTaskItemEntity i where i.id = :id")
    Optional<GradingTaskItemEntity> findByIdForUpdate(@Param("id") Long id);
}
