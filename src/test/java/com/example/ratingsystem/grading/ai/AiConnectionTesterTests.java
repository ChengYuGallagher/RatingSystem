package com.example.ratingsystem.grading.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiConnectionTesterTests {

    @Test
    void testsConnectionWithTheSuppliedRuntimeConfiguration() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiConnectionTester tester = new AiConnectionTester(builder.build());
        AiRuntimeConfig config = new AiRuntimeConfig(
                "test-secret", URI.create("https://api.example.test"), "test-model", 16);

        server.expect(once(), requestTo("https://api.example.test/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-secret"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.max_tokens").value(16))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"OK"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> tester.test(config));
        server.verify();
    }

    @Test
    void reportsAuthenticationFailureWithoutEchoingTheKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiConnectionTester tester = new AiConnectionTester(builder.build());
        AiRuntimeConfig config = new AiRuntimeConfig(
                "do-not-leak", URI.create("https://api.example.test"), "test-model", 16);

        server.expect(once(), requestTo("https://api.example.test/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        AiConnectionException exception = assertThrows(
                AiConnectionException.class, () -> tester.test(config));

        assertEquals("连接测试未通过，请检查 API Key 是否正确", exception.getMessage());
        server.verify();
    }
}
