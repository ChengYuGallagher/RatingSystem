package com.example.ratingsystem.grading.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rating.ai-settings")
public record AiSettingsProperties(String directory) {
}
