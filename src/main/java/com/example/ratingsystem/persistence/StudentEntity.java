package com.example.ratingsystem.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "students")
class StudentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String studentNo;
    private String name;

    @CreationTimestamp
    private Instant createdAt;

    protected StudentEntity() {
    }

    StudentEntity(String studentNo, String name) {
        this.studentNo = studentNo;
        this.name = name;
    }

    Long getId() {
        return id;
    }

    String getStudentNo() {
        return studentNo;
    }

    String getName() {
        return name;
    }
}
