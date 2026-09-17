package com.example.ratingsystem.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface StudentJpaRepository extends JpaRepository<StudentEntity, Long> {
    Optional<StudentEntity> findByStudentNo(String studentNo);
}
