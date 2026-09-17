package com.example.ratingsystem.persistence;

import com.example.ratingsystem.grading.ai.AiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PersistenceApiIntegrationTests.MockAiConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PersistenceApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubAiClient aiClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAi() {
        aiClient.reset();
    }

    @Test
    void persistsSuccessfulResultsAndReturnsExistingResultWithoutSecondAiCall() throws Exception {
        ExamIds exam = createExamWithChoiceAndShortAnswer();
        long submissionId = createSubmission(exam, "2026001");

        JsonNode first = performJson(post("/api/submissions/{id}/grading", submissionId))
                .get("results");

        assertEquals(2, first.size());
        assertEquals("SUCCESS", first.get(0).get("gradingStatus").stringValue());
        assertEquals(0, first.get(0).get("suggestedScore").decimalValue().compareTo(new java.math.BigDecimal("5")));
        assertEquals("SUCCESS", first.get(1).get("gradingStatus").stringValue());
        assertEquals(0, first.get(1).get("suggestedScore").decimalValue().compareTo(new java.math.BigDecimal("4")));
        assertTrue(first.get(1).get("actualScore").isNull());
        assertEquals("PENDING", first.get(1).get("reviewStatus").stringValue());
        assertEquals(exam.rubricItemId(), first.get(1).get("criterionScores").get(0).get("rubricItemId").longValue());
        assertEquals(1, aiClient.calls());

        JsonNode repeated = performJson(post("/api/submissions/{id}/grading", submissionId));
        assertEquals("SUCCESS", repeated.get("results").get(1).get("gradingStatus").stringValue());
        assertEquals(1, aiClient.calls(), "重复普通评分请求不能再次调用 AI");

        JsonNode queried = performJson(get("/api/submissions/{id}/results", submissionId));
        assertEquals(2, queried.size());
        assertEquals("学生说明了核心机制", queried.get(1).get("studentAnswer").stringValue());
    }

    @Test
    void persistsAiFailureWithoutZeroAndRetriesOnlyThroughExplicitEndpoint() throws Exception {
        ExamIds exam = createExamWithChoiceAndShortAnswer();
        long submissionId = createSubmission(exam, "2026002");
        aiClient.returnInvalidJson();

        JsonNode failed = performJson(post("/api/submissions/{id}/grading", submissionId)).get("results").get(1);
        long resultId = failed.get("id").longValue();
        assertEquals("FAILED", failed.get("gradingStatus").stringValue());
        assertTrue(failed.get("suggestedScore").isNull());
        assertTrue(failed.get("actualScore").isNull());
        assertEquals("PENDING", failed.get("reviewStatus").stringValue());
        assertEquals(1, aiClient.calls());

        performJson(post("/api/submissions/{id}/grading", submissionId));
        assertEquals(1, aiClient.calls(), "失败结果不能由普通接口自动重试");

        aiClient.returnValidJson();
        JsonNode retried = performJson(post("/api/grading/results/{id}/retry", resultId));
        assertEquals("SUCCESS", retried.get("gradingStatus").stringValue());
        assertEquals(2, retried.get("attemptCount").intValue());
        assertEquals(2, aiClient.calls());
    }

    @Test
    void marksStaleRunningResultFailedWithoutCallingAiAgain() throws Exception {
        ExamIds exam = createExamWithChoiceAndShortAnswer();
        long submissionId = createSubmission(exam, "2026003");
        aiClient.returnInvalidJson();
        JsonNode result = performJson(post("/api/submissions/{id}/grading", submissionId)).get("results").get(1);
        long resultId = result.get("id").longValue();
        int callsBeforeQuery = aiClient.calls();

        jdbcTemplate.update("""
                update grading_results
                   set grading_status = 'RUNNING', failure_message = null,
                       running_since = DATEADD('MINUTE', -10, CURRENT_TIMESTAMP)
                 where id = ?
                """, resultId);

        JsonNode queried = performJson(get("/api/submissions/{id}/results", submissionId));
        JsonNode stale = findResult(queried, resultId);
        assertEquals("FAILED", stale.get("gradingStatus").stringValue());
        assertTrue(stale.get("failureMessage").stringValue().contains("显式重试"));
        assertEquals(callsBeforeQuery, aiClient.calls());
    }

    @Test
    void rejectsInvalidRubricAndDuplicateSubmission() throws Exception {
        mockMvc.perform(post("/api/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"错误评分规则","questions":[{
                                  "questionNo":1,"questionType":"SHORT_ANSWER","content":"题目",
                                  "maxScore":10,"referenceAnswer":"参考答案",
                                  "rubricItems":[{"itemOrder":1,"name":"评分点","maxScore":9}]
                                }]}
                                """))
                .andExpect(status().isBadRequest());

        ExamIds exam = createExamWithChoiceAndShortAnswer();
        createSubmission(exam, "2026004");
        mockMvc.perform(post("/api/exams/{id}/submissions", exam.examId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionJson(exam, "2026004")))
                .andExpect(status().isConflict());
    }

    private ExamIds createExamWithChoiceAndShortAnswer() throws Exception {
        JsonNode created = performJson(post("/api/exams")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"持久化测试考试","questions":[
                          {"questionNo":1,"questionType":"CHOICE","content":"请选择正确选项",
                           "maxScore":5,"referenceAnswer":"B"},
                          {"questionNo":2,"questionType":"SHORT_ANSWER","content":"说明核心机制",
                           "maxScore":5,"referenceAnswer":"完整参考答案",
                           "rubricItems":[{"itemOrder":1,"name":"核心机制","maxScore":5}]}
                        ]}
                        """));
        return new ExamIds(
                created.get("id").longValue(),
                created.get("questions").get(0).get("id").longValue(),
                created.get("questions").get(1).get("id").longValue(),
                created.get("questions").get(1).get("rubricItems").get(0).get("id").longValue()
        );
    }

    private long createSubmission(ExamIds exam, String studentNo) throws Exception {
        return performJson(post("/api/exams/{id}/submissions", exam.examId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(submissionJson(exam, studentNo))).get("id").longValue();
    }

    private String submissionJson(ExamIds exam, String studentNo) {
        return """
                {"studentNo":"%s","studentName":"测试学生","answers":[
                  {"questionId":%d,"answerText":"B"},
                  {"questionId":%d,"answerText":"学生说明了核心机制"}
                ]}
                """.formatted(studentNo, exam.choiceQuestionId(), exam.shortQuestionId());
    }

    private JsonNode performJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }

    private JsonNode findResult(JsonNode results, long resultId) {
        for (JsonNode result : results) {
            if (result.get("id").longValue() == resultId) {
                return result;
            }
        }
        throw new AssertionError("未找到评分结果: " + resultId);
    }

    private record ExamIds(long examId, long choiceQuestionId, long shortQuestionId, long rubricItemId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockAiConfiguration {
        @Bean
        @Primary
        StubAiClient stubAiClient(ObjectMapper objectMapper) {
            return new StubAiClient(objectMapper);
        }
    }

    static class StubAiClient implements AiClient {
        private final ObjectMapper objectMapper;
        private final AtomicInteger callCount = new AtomicInteger();
        private volatile boolean invalidJson;

        StubAiClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            callCount.incrementAndGet();
            if (invalidJson) {
                return "not-json";
            }
            try {
                JsonNode material = objectMapper.readTree(userPrompt.substring(userPrompt.indexOf('\n') + 1));
                long rubricId = material.get("rubricItems").get(0).get("id").longValue();
                return """
                        {"items":[{"rubricItemId":%d,"criterion":"核心机制","maxScore":5,
                        "score":4,"reason":"覆盖核心机制但略有遗漏"}],
                        "suggestedScore":4,"reason":"主要内容正确"}
                        """.formatted(rubricId);
            } catch (Exception exception) {
                throw new AssertionError("测试桩无法解析评分材料", exception);
            }
        }

        int calls() {
            return callCount.get();
        }

        void returnInvalidJson() {
            invalidJson = true;
        }

        void returnValidJson() {
            invalidJson = false;
        }

        void reset() {
            callCount.set(0);
            invalidJson = false;
        }
    }
}
