package com.example.ratingsystem.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("rating.grading")
public record GradingPersistenceProperties(Duration runningTimeout) {
}
