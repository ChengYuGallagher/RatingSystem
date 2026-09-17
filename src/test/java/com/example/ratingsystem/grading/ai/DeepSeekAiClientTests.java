package com.example.ratingsystem.grading.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class DeepSeekAiClientTests {

    private MockRestServiceServer server;
    private DeepSeekAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DeepSeekAiClient(
                builder.build(),
                new AiProperties(
                        "test-key",
                        URI.create("https://api.deepseek.com"),
                        "deepseek-flash",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        1200
                )
        );
    }

    @Test
    void sendsJsonOutputRequestAndExtractsContent() {
        server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model":"deepseek-flash",
                          "response_format":{"type":"json_object"},
                          "max_tokens":1200,
                          "stream":false
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "choices":[{
                            "finish_reason":"stop",
                            "message":{"content":"{\\"suggestedScore\\":8}"}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        String content = client.complete("system", "user");

        assertEquals("{\"suggestedScore\":8}", content);
        server.verify();
    }

    @Test
    void rejectsTruncatedResponse() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices":[{
                            "finish_reason":"length",
                            "message":{"content":"{}"}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThrows(AiGradingException.class, () -> client.complete("system", "user"));
        server.verify();
    }

    @Test
    void rejectsHttpError() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(AiGradingException.class, () -> client.complete("system", "user"));
        server.verify();
    }

    @Test
    void rejectsReadTimeout() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(AiGradingException.class, () -> client.complete("system", "user"));
        server.verify();
    }

    @Test
    void rejectsMissingChoices() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(AiGradingException.class, () -> client.complete("system", "user"));
        server.verify();
    }

    @Test
    void rejectsEmptyContent() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices":[{
                            "finish_reason":"stop",
                            "message":{"content":" "}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThrows(AiGradingException.class, () -> client.complete("system", "user"));
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyWithoutSendingRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        MockRestServiceServer noRequestServer = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAiClient clientWithoutKey = new DeepSeekAiClient(
                builder.build(),
                new AiProperties(
                        " ",
                        URI.create("https://api.deepseek.com"),
                        "deepseek-flash",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        1200
                )
        );

        assertThrows(AiGradingException.class, () -> clientWithoutKey.complete("system", "user"));
        noRequestServer.verify();
    }
}
