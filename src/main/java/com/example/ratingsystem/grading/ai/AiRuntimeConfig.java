package com.example.ratingsystem.grading.ai;

import org.springframework.util.StringUtils;

import java.net.URI;

public record AiRuntimeConfig(
        String apiKey,
        URI baseUrl,
        String model,
        int maxTokens
) {
    public boolean configured() {
        return StringUtils.hasText(apiKey) && baseUrl != null && StringUtils.hasText(model);
    }

    public URI chatCompletionsUri() {
        String base = baseUrl.toString().replaceAll("/+$", "");
        return URI.create(base + "/chat/completions");
    }
}
