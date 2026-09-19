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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void acceptsSuggestionWithoutChangingOriginalAutomaticGradingDetails() throws Exception {
        ExamIds exam = createExamWithChoiceAndShortAnswer();
        long submissionId = createSubmission(exam, "2026010");
        JsonNode original = performJson(post("/api/submissions/{id}/grading", submissionId))
                .get("results").get(1);

        JsonNode reviewed = performJson(put("/api/grading/results/{id}/review", original.get("id").longValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("ACCEPT_SUGGESTION", null, original.get("version").longValue())));

        assertEquals("CONFIRMED", reviewed.get("reviewStatus").stringValue());
        assertScore("4", reviewed.get("actualScore"));
        assertScore("4", reviewed.get("suggestedScore"));
        assertEquals("主要内容正确", reviewed.get("reason").stringValue());
        assertEquals(original.get("criterionScores"), reviewed.get("criterionScores"));
        assertEquals(original.get("version").longValue() + 1, reviewed.get("version").longValue());
    }

    @Test
    void setsAndCorrectsActualScoreUsingLatestVersion() throws Exception {
        JsonNode original = createGradedShortAnswer("2026011").result();
        long resultId = original.get("id").longValue();

        JsonNode firstReview = performJson(put("/api/grading/results/{id}/review", resultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("SET_SCORE", "3.50", original.get("version").longValue())));
        JsonNode corrected = performJson(put("/api/grading/results/{id}/review", resultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("SET_SCORE", "2.25", firstReview.get("version").longValue())));

        assertScore("2.25", corrected.get("actualScore"));
        assertScore("4", corrected.get("suggestedScore"));
        assertEquals("CONFIRMED", corrected.get("reviewStatus").stringValue());
        assertEquals(original.get("version").longValue() + 2, corrected.get("version").longValue());
        assertEquals("主要内容正确", corrected.get("reason").stringValue());
        assertEquals(original.get("criterionScores"), corrected.get("criterionScores"));
    }

    @Test
    void rejectsInvalidReviewRequestsAndMissingResult() throws Exception {
        JsonNode original = createGradedShortAnswer("2026012").result();
        long resultId = original.get("id").longValue();
        long version = original.get("version").longValue();

        assertReviewStatus(resultId, reviewBody("SET_SCORE", "-0.01", version), 400);
        assertReviewStatus(resultId, reviewBody("SET_SCORE", "5.01", version), 400);
        assertReviewStatus(resultId, reviewBody("SET_SCORE", "1.001", version), 400);
        assertReviewStatus(resultId, reviewBody("SET_SCORE", null, version), 400);
        assertReviewStatus(resultId,
                "{\"action\":\"ACCEPT_SUGGESTION\",\"actualScore\":4,\"expectedVersion\":"
                        + version + "}", 400);
        assertReviewStatus(resultId, "{\"expectedVersion\":" + version + "}", 400);
        assertReviewStatus(resultId, reviewBody("SET_SCORE", "4", -1), 400);
        assertReviewStatus(resultId, "{\"action\":\"SET_SCORE\",\"actualScore\":4}", 400);
        assertReviewStatus(999999L, reviewBody("SET_SCORE", "4", 0), 404);
    }

    @Test
    void allowsManualFallbackForFailedGradingButStillRejectsRunningAndAcceptSuggestion() throws Exception {
        ExamIds exam = createExamWithChoiceAndShortAnswer();
        long submissionId = createSubmission(exam, "2026013");
        aiClient.returnInvalidJson();
        JsonNode failed = performJson(post("/api/submissions/{id}/grading", submissionId))
                .get("results").get(1);

        long resultId = failed.get("id").longValue();
        long version = failed.get("version").longValue();
        assertReviewStatus(resultId, reviewBody("ACCEPT_SUGGESTION", null, version), 409);

        jdbcTemplate.update("""
                update grading_results
                   set grading_status = 'RUNNING', running_since = CURRENT_TIMESTAMP
                 where id = ?
                """, resultId);
        assertReviewStatus(resultId, reviewBody("SET_SCORE", "3", version), 409);

        jdbcTemplate.update("""
                update grading_results
                   set grading_status = 'FAILED', running_since = null
                 where id = ?
                """, resultId);
        JsonNode reviewed = performJson(put("/api/grading/results/{id}/review", resultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("SET_SCORE", "3", version)));
        assertScore("3", reviewed.get("actualScore"));
        assertEquals("CONFIRMED", reviewed.get("reviewStatus").stringValue());

        JsonNode current = findResult(
                performJson(get("/api/submissions/{id}/results", submissionId)),
                resultId);
        assertScore("3", current.get("actualScore"));
        assertEquals("CONFIRMED", current.get("reviewStatus").stringValue());
    }

    @Test
    void returnsConflictForStaleVersionAndKeepsCommittedReview() throws Exception {
        GradedAnswer graded = createGradedShortAnswer("2026014");
        JsonNode original = graded.result();
        long resultId = original.get("id").longValue();
        long originalVersion = original.get("version").longValue();

        JsonNode accepted = performJson(put("/api/grading/results/{id}/review", resultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("SET_SCORE", "3.50", originalVersion)));
        assertEquals(originalVersion + 1, accepted.get("version").longValue());

        assertReviewStatus(resultId, reviewBody("SET_SCORE", "2", originalVersion), 409);

        JsonNode current = findResult(
                performJson(get("/api/submissions/{id}/results", graded.submissionId())), resultId);
        assertScore("3.50", current.get("actualScore"));
        assertEquals("CONFIRMED", current.get("reviewStatus").stringValue());
        assertEquals(originalVersion + 1, current.get("version").longValue());
    }

    @Test
    void concurrentReviewsDoNotSilentlyOverwriteEachOther() throws Exception {
        GradedAnswer graded = createGradedShortAnswer("2026015");
        JsonNode original = graded.result();
        long resultId = original.get("id").longValue();
        long originalVersion = original.get("version").longValue();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> first = executor.submit(() -> performConcurrentReview(
                    resultId, "3.25", originalVersion, ready, start));
            Future<MvcResult> second = executor.submit(() -> performConcurrentReview(
                    resultId, "2.75", originalVersion, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "两个并发请求应准备就绪");
            start.countDown();

            MvcResult firstResult = first.get(10, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(10, TimeUnit.SECONDS);
            int firstStatus = firstResult.getResponse().getStatus();
            int secondStatus = secondResult.getResponse().getStatus();
            assertTrue((firstStatus == 200 && secondStatus == 409)
                            || (firstStatus == 409 && secondStatus == 200),
                    "并发审核必须恰好一个成功、一个版本冲突");

            MvcResult successful = firstStatus == 200 ? firstResult : secondResult;
            JsonNode successfulBody = objectMapper.readTree(
                    successful.getResponse().getContentAsString(StandardCharsets.UTF_8));
            JsonNode current = findResult(
                    performJson(get("/api/submissions/{id}/results", graded.submissionId())), resultId);
            assertEquals(successfulBody.get("actualScore"), current.get("actualScore"));
            assertEquals("CONFIRMED", current.get("reviewStatus").stringValue());
            assertEquals(originalVersion + 1, successfulBody.get("version").longValue());
            assertEquals(originalVersion + 1, current.get("version").longValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void repeatedAutomaticGradingAndRetryCannotChangeConfirmedReviewOrCallAiAgain() throws Exception {
        ExamIds exam = createExamWithChoiceAndShortAnswer();
        long submissionId = createSubmission(exam, "2026016");
        JsonNode original = performJson(post("/api/submissions/{id}/grading", submissionId))
                .get("results").get(1);
        long resultId = original.get("id").longValue();

        JsonNode reviewed = performJson(put("/api/grading/results/{id}/review", resultId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("SET_SCORE", "3.50", original.get("version").longValue())));
        assertEquals(1, aiClient.calls());

        JsonNode repeated = findResult(
                performJson(post("/api/submissions/{id}/grading", submissionId)).get("results"), resultId);
        assertScore("3.50", repeated.get("actualScore"));
        assertEquals("CONFIRMED", repeated.get("reviewStatus").stringValue());
        assertEquals(reviewed.get("version"), repeated.get("version"));
        assertEquals(1, aiClient.calls(), "审核后的重复评分不能产生新的 AI 调用");

        mockMvc.perform(post("/api/grading/results/{id}/retry", resultId))
                .andExpect(status().isConflict());
        JsonNode afterRetry = findResult(
                performJson(get("/api/submissions/{id}/results", submissionId)), resultId);
        assertScore("3.50", afterRetry.get("actualScore"));
        assertEquals("CONFIRMED", afterRetry.get("reviewStatus").stringValue());
        assertEquals(1, aiClient.calls());
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

    private GradedAnswer createGradedShortAnswer(String studentNo) throws Exception {
        ExamIds exam = createExamWithChoiceAndShortAnswer();
        long submissionId = createSubmission(exam, studentNo);
        JsonNode result = performJson(post("/api/submissions/{id}/grading", submissionId))
                .get("results").get(1);
        return new GradedAnswer(submissionId, result);
    }

    private String reviewBody(String action, String actualScore, long expectedVersion) {
        String scoreProperty = actualScore == null ? "" : ",\"actualScore\":" + actualScore;
        return "{\"action\":\"%s\"%s,\"expectedVersion\":%d}"
                .formatted(action, scoreProperty, expectedVersion);
    }

    private void assertReviewStatus(long resultId, String body, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/grading/results/{id}/review", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    private MvcResult performConcurrentReview(long resultId, String actualScore, long expectedVersion,
                                              CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "并发审核应同时开始");
        return mockMvc.perform(put("/api/grading/results/{id}/review", resultId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("SET_SCORE", actualScore, expectedVersion)))
                .andReturn();
    }

    private void assertScore(String expected, JsonNode actual) {
        assertEquals(0, actual.decimalValue().compareTo(new BigDecimal(expected)));
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

    private record GradedAnswer(long submissionId, JsonNode result) {
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
