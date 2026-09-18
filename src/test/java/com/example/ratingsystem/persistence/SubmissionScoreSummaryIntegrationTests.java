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
import java.util.ArrayList;
import java.util.List;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SubmissionScoreSummaryIntegrationTests.MockAiConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SubmissionScoreSummaryIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubAiClient aiClient;

    @BeforeEach
    void resetAi() {
        aiClient.reset();
    }

    @Test
    void excludesSuggestionsAndPartialReviewsFromFinalScore() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5", "10", "5"), List.of("A", "A", "A"));
        JsonNode results = grade(setup.submissionId());

        JsonNode unreviewed = getSummary(setup.submissionId());
        assertEquals("INCOMPLETE", unreviewed.get("completionStatus").stringValue());
        assertEquals(3, unreviewed.get("successfullyGradedQuestionCount").intValue());
        assertScore("0.00", unreviewed.get("confirmedScore"));
        assertTrue(unreviewed.get("finalScore").isNull());

        review(results.get(0), "5");
        review(results.get(1), "8");
        JsonNode partial = getSummary(setup.submissionId());
        assertEquals(2, partial.get("confirmedQuestionCount").intValue());
        assertEquals(1, partial.get("unconfirmedQuestionCount").intValue());
        assertScore("13.00", partial.get("confirmedScore"));
        assertTrue(partial.get("finalScore").isNull());
    }

    @Test
    void calculatesCompleteScoreAndReflectsLaterCorrection() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5", "10", "5"), List.of("A", "A", "A"));
        JsonNode results = grade(setup.submissionId());

        review(results.get(0), "5");
        JsonNode secondReview = review(results.get(1), "8");
        review(results.get(2), "4");
        JsonNode complete = getSummary(setup.submissionId());
        assertEquals("COMPLETE", complete.get("completionStatus").stringValue());
        assertScore("17.00", complete.get("confirmedScore"));
        assertScore("17.00", complete.get("finalScore"));

        review(secondReview, "7");
        JsonNode corrected = getSummary(setup.submissionId());
        assertEquals("COMPLETE", corrected.get("completionStatus").stringValue());
        assertScore("16.00", corrected.get("finalScore"));
        assertEquals(corrected, getSummary(setup.submissionId()), "无数据变化时重复查询应一致");
    }

    @Test
    void countsConfirmedZeroAsReviewedScore() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5"), List.of("B"));
        JsonNode result = grade(setup.submissionId()).get(0);
        assertScore("0", result.get("suggestedScore"));

        review(result, "0");
        JsonNode summary = getSummary(setup.submissionId());
        assertEquals(1, summary.get("confirmedQuestionCount").intValue());
        assertEquals(0, summary.get("unconfirmedQuestionCount").intValue());
        assertEquals("COMPLETE", summary.get("completionStatus").stringValue());
        assertScore("0.00", summary.get("finalScore"));
    }

    @Test
    void blankAnswerCanCompleteOnlyAfterTeacherConfirmsZero() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5"), List.of(""));
        JsonNode result = grade(setup.submissionId()).get(0);

        JsonNode beforeReview = getSummary(setup.submissionId());
        assertEquals(1, beforeReview.get("answerRecordCount").intValue());
        assertEquals("INCOMPLETE", beforeReview.get("completionStatus").stringValue());
        assertTrue(beforeReview.get("finalScore").isNull());

        review(result, "0");
        JsonNode complete = getSummary(setup.submissionId());
        assertEquals("COMPLETE", complete.get("completionStatus").stringValue());
        assertScore("0.00", complete.get("finalScore"));
    }

    @Test
    void addsDecimalScoresExactly() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("0.10", "0.20"), List.of("A", "A"));
        JsonNode results = grade(setup.submissionId());

        review(results.get(0), "0.10");
        review(results.get(1), "0.20");
        JsonNode summary = getSummary(setup.submissionId());

        assertScore("0.30", summary.get("examMaxScore"));
        assertScore("0.30", summary.get("confirmedScore"));
        assertScore("0.30", summary.get("finalScore"));
    }

    @Test
    void reportsSubmissionWithoutGradingAsIncomplete() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5", "5"), List.of("A", "A"));

        JsonNode summary = getSummary(setup.submissionId());

        assertEquals(2, summary.get("questionCount").intValue());
        assertEquals(2, summary.get("answerRecordCount").intValue());
        assertEquals(0, summary.get("gradingResultCount").intValue());
        assertEquals("INCOMPLETE", summary.get("completionStatus").stringValue());
        assertTrue(summary.get("finalScore").isNull());
    }

    @Test
    void missingAnswerPreventsFinalScoreEvenWhenRemainingRecordsExist() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5", "5"), List.of("A", "A"));
        jdbcTemplate.update("delete from student_answers where submission_id = ? and question_id = ?",
                setup.submissionId(), setup.questionIds().get(1));

        JsonNode summary = getSummary(setup.submissionId());

        assertEquals(2, summary.get("questionCount").intValue());
        assertEquals(1, summary.get("answerRecordCount").intValue());
        assertEquals("INCOMPLETE", summary.get("completionStatus").stringValue());
        assertTrue(summary.get("finalScore").isNull());
    }

    @Test
    void missingGradingResultPreventsFinalScore() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5", "5"), List.of("A", "A"));
        JsonNode results = grade(setup.submissionId());
        review(results.get(0), "5");
        review(results.get(1), "4");
        jdbcTemplate.update("delete from grading_results where id = ?", results.get(1).get("id").longValue());

        JsonNode summary = getSummary(setup.submissionId());

        assertEquals(1, summary.get("gradingResultCount").intValue());
        assertEquals(1, summary.get("confirmedQuestionCount").intValue());
        assertScore("5.00", summary.get("confirmedScore"));
        assertEquals("INCOMPLETE", summary.get("completionStatus").stringValue());
        assertTrue(summary.get("finalScore").isNull());
    }

    @Test
    void inconsistentReviewStatusAndActualScoreCannotBecomeFinal() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5"), List.of("A"));
        JsonNode result = grade(setup.submissionId()).get(0);
        jdbcTemplate.update("update grading_results set review_status = 'CONFIRMED', actual_score = null where id = ?",
                result.get("id").longValue());

        JsonNode summary = getSummary(setup.submissionId());

        assertEquals(0, summary.get("confirmedQuestionCount").intValue());
        assertScore("0.00", summary.get("confirmedScore"));
        assertEquals("INCOMPLETE", summary.get("completionStatus").stringValue());
        assertTrue(summary.get("finalScore").isNull());
    }

    @Test
    void equalCountsWithWrongQuestionAssociationCannotBecomeFinal() throws Exception {
        SubmissionSetup original = createChoiceSubmission(List.of("5"), List.of("A"));
        JsonNode result = grade(original.submissionId()).get(0);
        review(result, "5");
        SubmissionSetup otherExam = createChoiceSubmission(List.of("5"), List.of("A"));
        jdbcTemplate.update("update student_answers set question_id = ? where submission_id = ?",
                otherExam.questionIds().get(0), original.submissionId());

        JsonNode summary = getSummary(original.submissionId());

        assertEquals(1, summary.get("questionCount").intValue());
        assertEquals(1, summary.get("answerRecordCount").intValue());
        assertEquals(1, summary.get("gradingResultCount").intValue());
        assertEquals(0, summary.get("successfullyGradedQuestionCount").intValue());
        assertEquals(0, summary.get("confirmedQuestionCount").intValue());
        assertEquals("INCOMPLETE", summary.get("completionStatus").stringValue());
        assertTrue(summary.get("finalScore").isNull());
    }

    @Test
    void failedAutomaticGradingDoesNotProduceFinalScore() throws Exception {
        long submissionId = createShortAnswerSubmission();
        aiClient.returnInvalidJson();

        JsonNode result = grade(submissionId).get(0);
        JsonNode summary = getSummary(submissionId);

        assertEquals("FAILED", result.get("gradingStatus").stringValue());
        assertEquals(1, summary.get("gradingResultCount").intValue());
        assertEquals(0, summary.get("successfullyGradedQuestionCount").intValue());
        assertEquals(0, summary.get("confirmedQuestionCount").intValue());
        assertEquals("INCOMPLETE", summary.get("completionStatus").stringValue());
        assertTrue(summary.get("finalScore").isNull());
        assertEquals(1, aiClient.calls());
    }

    @Test
    void returnsNotFoundForUnknownSubmission() throws Exception {
        mockMvc.perform(get("/api/submissions/{id}/summary", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void zeroQuestionExamCannotProduceZeroPointFinalScore() throws Exception {
        jdbcTemplate.update("""
                insert into exams (name, status, created_at, updated_at)
                values ('零题考试', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long examId = jdbcTemplate.queryForObject("select id from exams where name = '零题考试'", Long.class);
        jdbcTemplate.update("""
                insert into students (student_no, name, created_at)
                values ('ZERO-1', '零题学生', CURRENT_TIMESTAMP)
                """);
        Long studentId = jdbcTemplate.queryForObject(
                "select id from students where student_no = 'ZERO-1'", Long.class);
        jdbcTemplate.update("""
                insert into exam_submissions (exam_id, student_id, created_at)
                values (?, ?, CURRENT_TIMESTAMP)
                """, examId, studentId);
        Long submissionId = jdbcTemplate.queryForObject(
                "select id from exam_submissions where exam_id = ? and student_id = ?",
                Long.class, examId, studentId);

        JsonNode summary = getSummary(submissionId);

        assertEquals(0, summary.get("questionCount").intValue());
        assertScore("0.00", summary.get("examMaxScore"));
        assertEquals("INCOMPLETE", summary.get("completionStatus").stringValue());
        assertTrue(summary.get("finalScore").isNull());
    }

    @Test
    void concurrentReviewAndSummaryReturnsOneConsistentSnapshot() throws Exception {
        SubmissionSetup setup = createChoiceSubmission(List.of("5", "5"), List.of("A", "A"));
        JsonNode results = grade(setup.submissionId());
        review(results.get(0), "5");
        JsonNode second = results.get(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> reviewFuture = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return mockMvc.perform(put("/api/grading/results/{id}/review", second.get("id").longValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewBody("5", second.get("version").longValue())))
                        .andReturn();
            });
            Future<MvcResult> summaryFuture = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return mockMvc.perform(get("/api/submissions/{id}/summary", setup.submissionId())).andReturn();
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            MvcResult reviewResponse = reviewFuture.get(10, TimeUnit.SECONDS);
            MvcResult summaryResponse = summaryFuture.get(10, TimeUnit.SECONDS);
            assertEquals(200, reviewResponse.getResponse().getStatus());
            assertEquals(200, summaryResponse.getResponse().getStatus());

            JsonNode concurrentSummary = objectMapper.readTree(
                    summaryResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
            boolean beforeCommit = "INCOMPLETE".equals(concurrentSummary.get("completionStatus").stringValue())
                    && concurrentSummary.get("finalScore").isNull()
                    && concurrentSummary.get("confirmedQuestionCount").intValue() == 1
                    && scoreEquals("5.00", concurrentSummary.get("confirmedScore"));
            boolean afterCommit = "COMPLETE".equals(concurrentSummary.get("completionStatus").stringValue())
                    && concurrentSummary.get("confirmedQuestionCount").intValue() == 2
                    && scoreEquals("10.00", concurrentSummary.get("confirmedScore"))
                    && scoreEquals("10.00", concurrentSummary.get("finalScore"));
            assertTrue(beforeCommit || afterCommit, "并发汇总必须对应审核提交前或提交后的完整快照");

            JsonNode finalSummary = getSummary(setup.submissionId());
            assertEquals("COMPLETE", finalSummary.get("completionStatus").stringValue());
            assertScore("10.00", finalSummary.get("finalScore"));
        } finally {
            executor.shutdownNow();
        }
    }

    private SubmissionSetup createChoiceSubmission(List<String> maxScores, List<String> answers) throws Exception {
        StringBuilder questions = new StringBuilder();
        for (int index = 0; index < maxScores.size(); index++) {
            if (index > 0) {
                questions.append(',');
            }
            questions.append("""
                    {"questionNo":%d,"questionType":"CHOICE","content":"选择题%d",
                     "maxScore":%s,"referenceAnswer":"A"}
                    """.formatted(index + 1, index + 1, maxScores.get(index)));
        }
        JsonNode exam = performJson(post("/api/exams")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"成绩汇总测试\",\"questions\":[" + questions + "]}"));
        List<Long> questionIds = new ArrayList<>();
        StringBuilder answerJson = new StringBuilder();
        for (int index = 0; index < answers.size(); index++) {
            long questionId = exam.get("questions").get(index).get("id").longValue();
            questionIds.add(questionId);
            if (index > 0) {
                answerJson.append(',');
            }
            answerJson.append("{\"questionId\":%d,\"answerText\":\"%s\"}"
                    .formatted(questionId, answers.get(index)));
        }
        JsonNode submission = performJson(post("/api/exams/{id}/submissions", exam.get("id").longValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentNo":"SUMMARY-1","studentName":"汇总测试学生","answers":[%s]}
                        """.formatted(answerJson)));
        return new SubmissionSetup(submission.get("id").longValue(), questionIds);
    }

    private long createShortAnswerSubmission() throws Exception {
        JsonNode exam = performJson(post("/api/exams")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"失败汇总测试","questions":[{
                          "questionNo":1,"questionType":"SHORT_ANSWER","content":"说明机制",
                          "maxScore":5,"referenceAnswer":"参考答案",
                          "rubricItems":[{"itemOrder":1,"name":"核心机制","maxScore":5}]
                        }]}
                        """));
        long examId = exam.get("id").longValue();
        long questionId = exam.get("questions").get(0).get("id").longValue();
        return performJson(post("/api/exams/{id}/submissions", examId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentNo":"SUMMARY-AI","studentName":"AI失败学生",
                         "answers":[{"questionId":%d,"answerText":"待评分答案"}]}
                        """.formatted(questionId))).get("id").longValue();
    }

    private JsonNode grade(long submissionId) throws Exception {
        return performJson(post("/api/submissions/{id}/grading", submissionId)).get("results");
    }

    private JsonNode review(JsonNode result, String actualScore) throws Exception {
        return performJson(put("/api/grading/results/{id}/review", result.get("id").longValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody(actualScore, result.get("version").longValue())));
    }

    private String reviewBody(String actualScore, long expectedVersion) {
        return "{\"action\":\"SET_SCORE\",\"actualScore\":%s,\"expectedVersion\":%d}"
                .formatted(actualScore, expectedVersion);
    }

    private JsonNode getSummary(long submissionId) throws Exception {
        return performJson(get("/api/submissions/{id}/summary", submissionId));
    }

    private JsonNode performJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }

    private void assertScore(String expected, JsonNode actual) {
        assertTrue(scoreEquals(expected, actual), () -> "期望分数 " + expected + "，实际为 " + actual);
    }

    private boolean scoreEquals(String expected, JsonNode actual) {
        return actual != null && !actual.isNull()
                && actual.decimalValue().compareTo(new BigDecimal(expected)) == 0;
    }

    private record SubmissionSetup(long submissionId, List<Long> questionIds) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockAiConfiguration {
        @Bean
        @Primary
        StubAiClient stubAiClient() {
            return new StubAiClient();
        }
    }

    static class StubAiClient implements AiClient {
        private final AtomicInteger callCount = new AtomicInteger();
        private volatile boolean invalidJson;

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            callCount.incrementAndGet();
            return invalidJson ? "not-json" : "{}";
        }

        int calls() {
            return callCount.get();
        }

        void returnInvalidJson() {
            invalidJson = true;
        }

        void reset() {
            callCount.set(0);
            invalidJson = false;
        }
    }
}
