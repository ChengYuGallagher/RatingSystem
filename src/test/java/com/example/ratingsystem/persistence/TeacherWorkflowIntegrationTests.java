package com.example.ratingsystem.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TeacherWorkflowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exposesTeacherPageAndFullExamStandardDetails() throws Exception {
        JsonNode exam = createExam("教师网页方案");

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RatingSystem")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"zip-file\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"exam-library-view\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"settings-panel\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"show-exam-library\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"exam-library-search\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"management-exam-list\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"management-ai\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"delete-exam-dialog\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"cancel-delete-exam\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"confirm-delete-exam\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-management-tab"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-open-ai-settings"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"exam-picker-list\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"show-ai-import\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"confirm-import-grade\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"ai-api-key\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/app.js")));
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("saveQuestionStandard")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pendingConfirmation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/exam-import/preview")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/confirm-import-and-grade")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/settings/ai")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("function showExamLibrary()")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("function showSettings()")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("function openDeleteExamDialog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("function closeDeleteExamDialog")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("showManagementTab"))));

        JsonNode exams = performJson(get("/api/exams"));
        JsonNode listed = findById(exams, exam.get("id").longValue());
        assertEquals(false, listed.get("standardsReviewed").booleanValue());
        assertEquals(2, listed.get("questionCount").intValue());
        assertScore("12", listed.get("maxScore"));

        JsonNode detail = performJson(get("/api/exams/{id}", exam.get("id").longValue()));
        assertEquals("参考程序", detail.get("questions").get(1).get("referenceAnswer").stringValue());
        assertEquals("教师根据正确性与完整性人工给分",
                detail.get("questions").get(1).get("gradingCriteria").stringValue());
    }

    @Test
    void editsStandardsPersistsChangesInvalidatesConfirmationAndPreservesImportedAnswers() throws Exception {
        JsonNode exam = performJson(post("/api/exams")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"评分标准编辑","questions":[{
                          "questionNo":1,"questionType":"SHORT_ANSWER","content":"说明封装",
                          "maxScore":10,"referenceAnswer":"旧参考答案","gradingCriteria":"旧评分细则",
                          "rubricItems":[
                            {"itemOrder":1,"name":"旧评分点一","maxScore":4},
                            {"itemOrder":2,"name":"旧评分点二","maxScore":6}
                          ]
                        }]}
                        """), 201);
        long examId = exam.get("id").longValue();
        long questionId = exam.get("questions").get(0).get("id").longValue();
        long submissionId = performJson(post("/api/exams/{id}/submissions", examId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentNo":"EDIT-001","studentName":"标准编辑学生","answers":[
                          {"questionId":%d,"answerText":"原始学生答案不得变化"}
                        ]}
                        """.formatted(questionId)), 201).get("id").longValue();

        assertTrue(performJson(put("/api/exams/{id}/standards/confirm", examId))
                .get("standardsReviewed").booleanValue());
        JsonNode updated = performJson(put("/api/exams/{examId}/questions/{questionId}/standards",
                        examId, questionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"referenceAnswer":"新参考答案","maxScore":12.50,"gradingCriteria":"新评分细则",
                         "rubricItems":[
                           {"itemOrder":1,"name":"核心概念","maxScore":5},
                           {"itemOrder":2,"name":"解释完整","maxScore":5}
                         ]}
                        """));

        assertEquals(false, updated.get("standardsReviewed").booleanValue());
        assertTrue(updated.get("standardsReviewedAt").isNull());
        JsonNode question = updated.get("questions").get(0);
        assertEquals("新参考答案", question.get("referenceAnswer").stringValue());
        assertScore("12.50", question.get("maxScore"));
        assertEquals("新评分细则", question.get("gradingCriteria").stringValue());
        assertEquals("核心概念", question.get("rubricItems").get(0).get("name").stringValue());
        assertScore("5", question.get("rubricItems").get(1).get("maxScore"));

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from exam_submissions where id = ?", Integer.class, submissionId));
        assertEquals("原始学生答案不得变化", jdbcTemplate.queryForObject("""
                select answer_text from student_answers
                 where submission_id = ? and question_id = ?
                """, String.class, submissionId, questionId));
        mockMvc.perform(post("/api/exams/{id}/grading-tasks", examId))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/exams/{examId}/questions/{questionId}/standards", examId, questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"referenceAnswer":"非法","maxScore":8,"gradingCriteria":"非法",
                                 "rubricItems":[
                                   {"itemOrder":1,"name":"一","maxScore":5},
                                   {"itemOrder":2,"name":"二","maxScore":4}
                                 ]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/exams/{examId}/questions/{questionId}/standards", examId, questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"referenceAnswer":"非法","maxScore":-1,"gradingCriteria":null,
                                 "rubricItems":[]}
                                """))
                .andExpect(status().isBadRequest());

        JsonNode unchanged = performJson(get("/api/exams/{id}", examId)).get("questions").get(0);
        assertEquals("新参考答案", unchanged.get("referenceAnswer").stringValue());
        assertScore("12.50", unchanged.get("maxScore"));
        assertTrue(performJson(put("/api/exams/{id}/standards/confirm", examId))
                .get("standardsReviewed").booleanValue());
    }

    @Test
    void requiresReviewedStandardsAndCompletesBatchGradingManualFallbackAndClassResults() throws Exception {
        JsonNode exam = createExam("批量评分闭环");
        long examId = exam.get("id").longValue();
        long choiceId = exam.get("questions").get(0).get("id").longValue();
        long programmingId = exam.get("questions").get(1).get("id").longValue();
        long firstSubmission = createSubmission(examId, choiceId, programmingId, "WEB-001", "甲同学");
        long secondSubmission = createSubmission(examId, choiceId, programmingId, "WEB-002", "乙同学");

        mockMvc.perform(post("/api/exams/{id}/grading-tasks", examId))
                .andExpect(status().isConflict());

        JsonNode confirmedExam = performJson(put("/api/exams/{id}/standards/confirm", examId));
        assertTrue(confirmedExam.get("standardsReviewed").booleanValue());
        assertNotNull(confirmedExam.get("standardsReviewedAt"));

        JsonNode started = performJson(post("/api/exams/{id}/grading-tasks", examId), 202);
        long taskId = started.get("id").longValue();
        JsonNode finished = awaitTask(taskId);
        assertEquals("COMPLETED", finished.get("status").stringValue());
        assertEquals(2, finished.get("successCount").intValue());
        assertEquals(0, finished.get("failedCount").intValue());

        JsonNode latest = performJson(get("/api/exams/{id}/grading-tasks/latest", examId));
        assertEquals(taskId, latest.get("id").longValue());

        mockMvc.perform(put("/api/exams/{examId}/questions/{questionId}/standards", examId, programmingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"referenceAnswer":"新参考程序","maxScore":10,
                                 "gradingCriteria":"新细则","rubricItems":[]}
                                """))
                .andExpect(status().isConflict());

        JsonNode repeated = performJson(post("/api/exams/{id}/grading-tasks", examId), 202);
        assertEquals(taskId, repeated.get("id").longValue());
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from grading_tasks where exam_id = ?", Integer.class, examId));

        reviewSubmission(firstSubmission, "6");
        reviewSubmission(secondSubmission, "5.50");

        JsonNode firstSummary = performJson(get("/api/submissions/{id}/summary", firstSubmission));
        assertEquals("COMPLETE", firstSummary.get("completionStatus").stringValue());
        assertScore("8", firstSummary.get("finalScore"));

        JsonNode classResults = performJson(get("/api/exams/{id}/class-results", examId));
        assertEquals(2, classResults.get("submissions").size());
        JsonNode first = findSubmission(classResults.get("submissions"), firstSubmission);
        JsonNode second = findSubmission(classResults.get("submissions"), secondSubmission);
        assertScore("8", first.get("finalScore"));
        assertScore("7.50", second.get("finalScore"));
        assertEquals(2, first.get("confirmedQuestionCount").intValue());
    }

    @Test
    void retriesOnlyFailedTaskItemsWithoutCreatingAnotherTaskOrDuplicateResults() throws Exception {
        JsonNode exam = createExam("批量重试保护");
        long examId = exam.get("id").longValue();
        long choiceId = exam.get("questions").get(0).get("id").longValue();
        long programmingId = exam.get("questions").get(1).get("id").longValue();
        long submissionId = createSubmission(examId, choiceId, programmingId, "WEB-003", "丙同学");
        performJson(put("/api/exams/{id}/standards/confirm", examId));

        JsonNode task = performJson(post("/api/exams/{id}/grading-tasks", examId), 202);
        long taskId = task.get("id").longValue();
        JsonNode finished = awaitTask(taskId);
        long itemId = finished.get("items").get(0).get("id").longValue();

        jdbcTemplate.update("update grading_task_items set status = 'FAILED', error_message = '测试故障' where id = ?", itemId);
        jdbcTemplate.update("""
                update grading_tasks
                   set status = 'PARTIAL_FAILED', processed_count = 1,
                       success_count = 0, failed_count = 1, completed_at = CURRENT_TIMESTAMP
                 where id = ?
                """, taskId);

        JsonNode retrying = performJson(post("/api/grading/tasks/{id}/retry-failed", taskId), 202);
        assertEquals(taskId, retrying.get("id").longValue());
        JsonNode retried = awaitTask(taskId);
        assertEquals("COMPLETED", retried.get("status").stringValue());
        assertEquals(2, retried.get("items").get(0).get("attemptCount").intValue());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from grading_tasks where exam_id = ?",
                Integer.class, examId));
        assertEquals(2, jdbcTemplate.queryForObject("""
                select count(*) from grading_results gr
                join student_answers sa on sa.id = gr.student_answer_id
                where sa.submission_id = ?
                """, Integer.class, submissionId));

        jdbcTemplate.update("""
                update grading_task_items
                   set status = 'RUNNING', updated_at = DATEADD('MINUTE', -10, CURRENT_TIMESTAMP)
                 where id = ?
                """, itemId);
        jdbcTemplate.update("""
                update grading_tasks
                   set status = 'RUNNING', processed_count = 0,
                       success_count = 0, failed_count = 0, completed_at = null,
                       started_at = DATEADD('MINUTE', -10, CURRENT_TIMESTAMP)
                 where id = ?
                """, taskId);
        JsonNode recovered = performJson(post("/api/exams/{id}/grading-tasks", examId), 202);
        assertEquals(taskId, recovered.get("id").longValue());
        JsonNode recoveredFinished = awaitTask(taskId);
        assertEquals("COMPLETED", recoveredFinished.get("status").stringValue());
        assertEquals(3, recoveredFinished.get("items").get(0).get("attemptCount").intValue());
    }

    private JsonNode createExam(String name) throws Exception {
        return performJson(post("/api/exams")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","questions":[
                          {"questionNo":1,"questionType":"CHOICE","content":"Java 文件扩展名？",
                           "maxScore":2,"referenceAnswer":".java"},
                          {"questionNo":2,"questionType":"PROGRAMMING","content":"编写示例程序",
                           "maxScore":10,"referenceAnswer":"参考程序",
                           "gradingCriteria":"教师根据正确性与完整性人工给分"}
                        ]}
                        """.formatted(name)), 201);
    }

    private long createSubmission(long examId, long choiceId, long programmingId,
                                  String studentNo, String studentName) throws Exception {
        JsonNode response = performJson(post("/api/exams/{id}/submissions", examId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentNo":"%s","studentName":"%s","answers":[
                          {"questionId":%d,"answerText":".java"},
                          {"questionId":%d,"answerText":"public class Main {}"}
                        ]}
                        """.formatted(studentNo, studentName, choiceId, programmingId)), 201);
        return response.get("id").longValue();
    }

    private void reviewSubmission(long submissionId, String programmingScore) throws Exception {
        JsonNode results = performJson(get("/api/submissions/{id}/results", submissionId));
        JsonNode choice = results.get(0);
        JsonNode programming = results.get(1);
        assertEquals("SUCCESS", choice.get("gradingStatus").stringValue());
        assertEquals("FAILED", programming.get("gradingStatus").stringValue());
        assertTrue(programming.get("suggestedScore").isNull());

        performJson(put("/api/grading/results/{id}/review", choice.get("id").longValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"ACCEPT_SUGGESTION","actualScore":null,"expectedVersion":%d}
                        """.formatted(choice.get("version").longValue())));
        JsonNode manual = performJson(put("/api/grading/results/{id}/review", programming.get("id").longValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"action":"SET_SCORE","actualScore":%s,"expectedVersion":%d}
                        """.formatted(programmingScore, programming.get("version").longValue())));
        assertEquals("FAILED", manual.get("gradingStatus").stringValue());
        assertEquals("CONFIRMED", manual.get("reviewStatus").stringValue());
    }

    private JsonNode awaitTask(long taskId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(8));
        JsonNode current;
        do {
            current = performJson(get("/api/grading/tasks/{id}", taskId));
            if (!"RUNNING".equals(current.get("status").stringValue())) {
                return current;
            }
            Thread.sleep(50);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("批量评分任务在测试超时时间内未完成");
    }

    private JsonNode performJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return performJson(request, 200);
    }

    private JsonNode performJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                 int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode findById(JsonNode values, long id) {
        for (JsonNode value : values) {
            if (value.get("id").longValue() == id) return value;
        }
        throw new AssertionError("未找到 id=" + id);
    }

    private JsonNode findSubmission(JsonNode values, long submissionId) {
        for (JsonNode value : values) {
            if (value.get("submissionId").longValue() == submissionId) return value;
        }
        throw new AssertionError("未找到 submissionId=" + submissionId);
    }

    private void assertScore(String expected, JsonNode value) {
        assertEquals(0, value.decimalValue().compareTo(new java.math.BigDecimal(expected)));
    }
}
