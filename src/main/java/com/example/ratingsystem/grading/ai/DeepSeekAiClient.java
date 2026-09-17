package com.example.ratingsystem.grading.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class DeepSeekAiClient implements AiClient {

    private final RestClient restClient;
    private final AiProperties properties;

    public DeepSeekAiClient(
            @Qualifier("deepSeekRestClient") RestClient restClient,
            AiProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AiGradingException("未配置 AI_API_KEY，无法执行 AI 评分");
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "thinking", Map.of("type", "disabled"),
                "max_tokens", properties.maxTokens(),
                "stream", false
        );

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(requestBody)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            return extractContent(response);
        } catch (RestClientException exception) {
            throw new AiGradingException("DeepSeek API 调用失败", exception);
        }
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AiGradingException("DeepSeek API 未返回评分结果");
        }

        Choice choice = response.choices().get(0);
        if (!"stop".equals(choice.finishReason())) {
            throw new AiGradingException("DeepSeek API 返回未完整结束，finish_reason=" + choice.finishReason());
        }
        if (choice.message() == null || !StringUtils.hasText(choice.message().content())) {
            throw new AiGradingException("DeepSeek API 返回了空评分内容");
        }
        return choice.message().content();
    }

    record ChatCompletionResponse(List<Choice> choices) {
    }

    record Choice(
            @JsonProperty("finish_reason") String finishReason,
            Message message
    ) {
    }

    record Message(String content) {
    }
}
