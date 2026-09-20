package com.example.ratingsystem.grading.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Component
public class AiConnectionTester {

    private final RestClient restClient;

    public AiConnectionTester(@Qualifier("deepSeekRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void test(AiRuntimeConfig config) {
        if (!config.configured()) {
            throw new AiConnectionException("AI 配置不完整，请填写 API Key、Base URL 和模型名称");
        }
        Map<String, Object> requestBody = Map.of(
                "model", config.model(),
                "messages", List.of(Map.of("role", "user", "content", "请只回复 OK")),
                "thinking", Map.of("type", "disabled"),
                "max_tokens", config.maxTokens(),
                "stream", false
        );
        try {
            JsonNode response = restClient.post()
                    .uri(config.chatCompletionsUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(config.apiKey()))
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("choices").isArray()
                    || response.path("choices").isEmpty()
                    || !StringUtils.hasText(response.path("choices").get(0).path("message").path("content").asText())) {
                throw new AiConnectionException("DeepSeek 已响应，但没有返回有效内容，请检查模型名称");
            }
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AiConnectionException("连接测试未通过，请检查 API Key 是否正确", exception);
            }
            throw new AiConnectionException("连接测试失败，DeepSeek 返回 HTTP " + status, exception);
        } catch (RestClientException exception) {
            throw new AiConnectionException("无法连接 DeepSeek，请检查 Base URL 和网络连接", exception);
        }
    }
}
