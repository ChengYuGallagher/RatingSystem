package com.example.ratingsystem.grading.ai;

public interface AiClient {

    String complete(String systemPrompt, String userPrompt);
}
