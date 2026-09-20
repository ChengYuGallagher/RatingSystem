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
    private final AiConfigProvider configProvider;

    public DeepSeekAiClient(
            @Qualifier("deepSeekRestClient") RestClient restClient,
            AiConfigProvider configProvider
    ) {
        this.restClient = restClient;
        this.configProvider = configProvider;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, userPrompt, -1);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int requestedMaxTokens) {
        AiRuntimeConfig config = configProvider.current();
        if (!config.configured()) {
            throw new AiGradingException("AI 尚未完整配置，请在系统设置中填写并保存配置");
        }
        int maxTokens = requestedMaxTokens > 0 ? Math.min(requestedMaxTokens, 16_000) : config.maxTokens();

        Map<String, Object> requestBody = Map.of(
                "model", config.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "thinking", Map.of("type", "disabled"),
                "max_tokens", maxTokens,
                "stream", false
        );

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(config.chatCompletionsUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(config.apiKey()))
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
