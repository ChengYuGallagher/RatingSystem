package com.example.ratingsystem.grading.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("rating.ai")
public record AiProperties(
        String apiKey,
        URI baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        int maxTokens
) {
}
