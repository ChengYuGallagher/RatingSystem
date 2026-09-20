package com.example.ratingsystem.grading.ai;

public interface AiClient {

    String complete(String systemPrompt, String userPrompt);

    default String complete(String systemPrompt, String userPrompt, int maxTokens) {
        return complete(systemPrompt, userPrompt);
    }
}
