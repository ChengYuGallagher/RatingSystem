package com.example.ratingsystem.batchimport;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BatchAnswerImportIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void parsesMultipleStudentsAndMapsExistingExamQuestions() throws Exception {
        ExamFixture exam = createExam();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("Java期末考试/10001_张三/10001_张三.docx", completeDocx("A", "B", "简答一", "代码一"));
        entries.put("Java期末考试/10002_李四/10002_李四.docx", completeDocx("B", "A", "简答二", "不会"));

        JsonNode batch = upload(exam.examId(), zip(entries));

        assertEquals(2, batch.get("uploadedStudentCount").intValue());
        assertEquals(2, batch.get("parseSuccessCount").intValue());
        assertEquals(0, batch.get("parseFailedCount").intValue());
        assertEquals(4, batch.get("students").get(0).get("recognizedQuestionCount").intValue());
        for (int index = 0; index < 4; index++) {
            assertEquals(exam.questionIds()[index],
                    batch.get("students").get(0).get("answers").get(index).get("questionId").longValue());
        }

        JsonNode reloaded = performJson(get("/api/answer-import/batches/{id}", batch.get("id").longValue()));
        assertEquals(batch.get("students").get(1).get("answers").get(3).get("rawAnswer"),
                reloaded.get("students").get(1).get("answers").get(3).get("rawAnswer"));
        JsonNode latest = performJson(get("/api/exams/{id}/answer-import/batches/latest", exam.examId()));
        assertEquals(batch.get("id").longValue(), latest.get("id").longValue());
    }

    @Test
    void mapsQuestionWithMultipleRubricItemsOnceAndPreservesEveryRubricItem() throws Exception {
        ExamFixture exam = createExam();

        assertEquals(3, jdbcTemplate.queryForObject(
                "select count(*) from question_rubric_items where question_id = ?",
                Integer.class, exam.questionIds()[2]));

        JsonNode batch = upload(exam.examId(), zip(Map.of(
                "班级/10014_多评分点/10014_多评分点.docx",
                completeDocx("A", "B", "完整简答", "完整代码"))));

        JsonNode student = batch.get("students").get(0);
        assertEquals(4, student.get("recognizedQuestionCount").intValue());
        assertEquals(4, student.get("answers").size());
        java.util.Set<Long> mappedQuestionIds = new java.util.HashSet<>();
        student.get("answers").forEach(answer -> mappedQuestionIds.add(answer.get("questionId").longValue()));
        assertEquals(4, mappedQuestionIds.size());
        assertEquals(exam.questionIds()[2], student.get("answers").get(2).get("questionId").longValue());

        assertEquals(3, jdbcTemplate.queryForObject(
                "select count(*) from question_rubric_items where question_id = ?",
                Integer.class, exam.questionIds()[2]));
    }

    @Test
    void rejectsAbnormalDuplicateQuestionNumberWithClearValidationError() throws Exception {
        ExamFixture exam = createExam();
        jdbcTemplate.update("update questions set question_no = 1 where id = ?", exam.questionIds()[1]);

        mockMvc.perform(multipart("/api/exams/{id}/answer-import/batches/preview", exam.examId())
                        .file(zipFile(zip(Map.of(
                                "班级/10015_异常题号/10015_异常题号.docx",
                                completeDocx("A", "B", "简答", "代码"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("考试题目数据异常：题号重复: 1"));
    }

    @Test
    void correctsIdentityAndAnswersThenConfirmsAndImportsWithoutDuplicates() throws Exception {
        ExamFixture exam = createExam();
        Map<String, byte[]> entries = Map.of(
                "班级/10003_王五/99999_错误姓名.docx", completeDocx("A", "B", "原简答", "原代码"));
        JsonNode batch = upload(exam.examId(), zip(entries));
        JsonNode student = batch.get("students").get(0);
        assertEquals("NEEDS_REVIEW", student.get("parseStatus").stringValue());

        long[] correctedMapping = exam.questionIds().clone();
        long firstQuestion = correctedMapping[0];
        correctedMapping[0] = correctedMapping[1];
        correctedMapping[1] = firstQuestion;
        String updateBody = correctionBody(student, "10003", "王五", correctedMapping, "修正答案");
        JsonNode corrected = performJson(put("/api/answer-import/batches/{batchId}/students/{studentId}",
                batch.get("id").longValue(), student.get("id").longValue())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody));
        assertEquals("修正答案", corrected.get("answers").get(0).get("rawAnswer").stringValue());
        assertEquals(correctedMapping[0], corrected.get("answers").get(0).get("questionId").longValue());
        assertNotEquals(student.get("version").longValue(), corrected.get("version").longValue());

        mockMvc.perform(put("/api/answer-import/batches/{batchId}/students/{studentId}",
                        batch.get("id").longValue(), student.get("id").longValue())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isConflict());

        JsonNode confirmed = performJson(post(
                "/api/answer-import/batches/{batchId}/students/{studentId}/confirm",
                batch.get("id").longValue(), student.get("id").longValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + corrected.get("version").longValue() + "}"));
        assertEquals("CONFIRMED", confirmed.get("reviewStatus").stringValue());

        JsonNode execution = performJson(post("/api/answer-import/batches/{id}/import",
                batch.get("id").longValue()));
        assertEquals(1, execution.get("importedCount").intValue());
        assertEquals(0, execution.get("remainingConfirmedCount").intValue());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from exam_submissions", Integer.class));
        assertEquals(4, jdbcTemplate.queryForObject("select count(*) from student_answers", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from grading_results", Integer.class));

        JsonNode importedBatch = performJson(get("/api/answer-import/batches/{id}", batch.get("id").longValue()));
        assertEquals("COMPLETED", importedBatch.get("status").stringValue());
        assertEquals(0, importedBatch.get("confirmedCount").intValue());
        assertEquals(1, importedBatch.get("importedCount").intValue());

        JsonNode repeated = performJson(post("/api/answer-import/batches/{id}/import",
                batch.get("id").longValue()));
        assertEquals(0, repeated.get("importedCount").intValue());
        assertEquals(0, repeated.get("remainingConfirmedCount").intValue());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from exam_submissions", Integer.class));
    }

    @Test
    void reportsDuplicateIdentityAndDoesNotLetOneCorruptDocumentBreakOthers() throws Exception {
        ExamFixture exam = createExam();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("班级/10004_甲/10004_甲.docx", completeDocx("A", "B", "正常", "代码"));
        entries.put("班级/10004_乙/10004_乙.docx", completeDocx("B", "A", "重复", "代码"));
        entries.put("班级/10005_丙/10005_丙.docx", "broken".getBytes(StandardCharsets.UTF_8));

        JsonNode batch = upload(exam.examId(), zip(entries));
        assertEquals(3, batch.get("uploadedStudentCount").intValue());
        assertEquals(2, batch.get("needsReviewCount").intValue());
        assertEquals(1, batch.get("parseFailedCount").intValue());
        assertTrue(issueCodes(batch.get("students").get(0)).contains("DUPLICATE_STUDENT_NO"));
        assertTrue(issueCodes(batch.get("students").get(2)).contains("DOCX_PARSE_FAILED"));
    }

    @Test
    void reportsMissingDocxAndMissingQuestionWithoutDiscardingRecords() throws Exception {
        ExamFixture exam = createExam();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("班级/10006_赵六/", null);
        entries.put("班级/10007_孙七/10007_孙七.docx", docx("1. A", "2. B", "1. 简答"));

        JsonNode batch = upload(exam.examId(), zip(entries));
        assertEquals(2, batch.get("uploadedStudentCount").intValue());
        JsonNode missingFile = findStudent(batch, "10006");
        JsonNode missingQuestion = findStudent(batch, "10007");
        assertEquals("FAILED", missingFile.get("parseStatus").stringValue());
        assertTrue(issueCodes(missingFile).contains("DOCX_MISSING"));
        assertEquals(3, missingQuestion.get("recognizedQuestionCount").intValue());
        assertEquals("FAILED", missingQuestion.get("answers").get(3).get("parseStatus").stringValue());
    }

    @Test
    void reportsMultipleDocxFilesInOneStudentFolder() throws Exception {
        ExamFixture exam = createExam();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("班级/10012_多文件/10012_多文件.docx", completeDocx("A", "B", "简答", "代码"));
        entries.put("班级/10012_多文件/10012_多文件_副本.docx", completeDocx("A", "B", "简答", "代码"));

        JsonNode batch = upload(exam.examId(), zip(entries));
        assertEquals(1, batch.get("uploadedStudentCount").intValue());
        assertEquals("FAILED", batch.get("students").get(0).get("parseStatus").stringValue());
        assertTrue(issueCodes(batch.get("students").get(0)).contains("MULTIPLE_DOCX_FILES"));
    }

    @Test
    void rejectsZipSlipAndEntryCountLimit() throws Exception {
        ExamFixture exam = createExam();
        mockMvc.perform(multipart("/api/exams/{id}/answer-import/batches/preview", exam.examId())
                        .file(zipFile(zip(Map.of("../10008_越界.docx", completeDocx("A", "B", "S", "P"))))))
                .andExpect(status().isBadRequest());

        Map<String, byte[]> manyEntries = new LinkedHashMap<>();
        for (int index = 0; index <= SafeZipReader.MAX_ENTRY_COUNT; index++) {
            manyEntries.put("班级/附件/file-" + index + ".txt", new byte[0]);
        }
        mockMvc.perform(multipart("/api/exams/{id}/answer-import/batches/preview", exam.examId())
                        .file(zipFile(zip(manyEntries))))
                .andExpect(status().isBadRequest());

        byte[] oversized = new byte[(int) SafeZipReader.MAX_SINGLE_FILE_SIZE + 1];
        mockMvc.perform(multipart("/api/exams/{id}/answer-import/batches/preview", exam.examId())
                        .file(zipFile(zip(Map.of("班级/10010_超大/10010_超大.docx", oversized)))))
                .andExpect(status().isBadRequest());

        String excessivePath = "班级/" + "a".repeat(SafeZipReader.MAX_PATH_LENGTH)
                + "/10013_超长路径.docx";
        mockMvc.perform(multipart("/api/exams/{id}/answer-import/batches/preview", exam.examId())
                        .file(zipFile(zip(Map.of(excessivePath, completeDocx("A", "B", "S", "P"))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordsExistingStudentIdentityConflictWithoutCreatingDuplicateSubmission() throws Exception {
        ExamFixture exam = createExam();
        mockMvc.perform(post("/api/exams/{id}/submissions", exam.examId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody(exam.questionIds(), "10011", "原姓名")))
                .andExpect(status().isCreated());

        JsonNode batch = upload(exam.examId(), zip(Map.of(
                "班级/10011_新姓名/10011_新姓名.docx", completeDocx("A", "B", "简答", "代码"))));
        JsonNode student = batch.get("students").get(0);
        performJson(post("/api/answer-import/batches/{batchId}/students/{studentId}/confirm",
                batch.get("id").longValue(), student.get("id").longValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + student.get("version").longValue() + "}"));

        JsonNode execution = performJson(post("/api/answer-import/batches/{id}/import",
                batch.get("id").longValue()));
        assertEquals(0, execution.get("importedCount").intValue());
        assertEquals(1, execution.get("failures").size());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from exam_submissions", Integer.class));

        JsonNode reloaded = performJson(get("/api/answer-import/batches/{id}", batch.get("id").longValue()));
        assertTrue(issueCodes(reloaded.get("students").get(0)).contains("FORMAL_IMPORT_FAILED"));
    }

    @Test
    void rejectsConfirmationUntilEveryMappingAndAnswerHasBeenCorrected() throws Exception {
        ExamFixture exam = createExam();
        JsonNode batch = upload(exam.examId(), zip(Map.of(
                "班级/10009_周八/10009_周八.docx", docx("1. A", "2. B", "1. 简答"))));
        JsonNode student = batch.get("students").get(0);

        mockMvc.perform(post("/api/answer-import/batches/{batchId}/students/{studentId}/confirm",
                        batch.get("id").longValue(), student.get("id").longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + student.get("version").longValue() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmsImportsAndStartsGradingInOneIdempotentOperation() throws Exception {
        ExamFixture exam = createChoiceOnlyExam();
        performJson(put("/api/exams/{id}/standards/confirm", exam.examId()));
        JsonNode batch = upload(exam.examId(), zip(Map.of(
                "班级/20001_甲/20001_甲.docx", docx("1. A"),
                "班级/20002_乙/20002_乙.docx", docx("1. B"))));

        JsonNode first = performJson(post(
                "/api/answer-import/batches/{id}/confirm-import-and-grade", batch.get("id").longValue()));
        assertEquals(2, first.get("automaticallyConfirmedCount").intValue());
        assertEquals(2, first.get("importResult").get("importedCount").intValue());
        long taskId = first.get("gradingTask").get("id").longValue();
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from exam_submissions", Integer.class));

        JsonNode repeated = performJson(post(
                "/api/answer-import/batches/{id}/confirm-import-and-grade", batch.get("id").longValue()));
        assertEquals(0, repeated.get("automaticallyConfirmedCount").intValue());
        assertEquals(0, repeated.get("importResult").get("importedCount").intValue());
        assertEquals(taskId, repeated.get("gradingTask").get("id").longValue());
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from exam_submissions", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from grading_tasks", Integer.class));
    }

    @Test
    void oneClickWorkflowBlocksUnresolvedAnswersAndUnreviewedStandardsWithoutSideEffects() throws Exception {
        ExamFixture exam = createChoiceOnlyExam();
        JsonNode normalBatch = upload(exam.examId(), zip(Map.of(
                "班级/20003_丙/20003_丙.docx", docx("1. A"))));
        mockMvc.perform(post("/api/answer-import/batches/{id}/confirm-import-and-grade",
                        normalBatch.get("id").longValue()))
                .andExpect(status().isConflict());
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from exam_submissions", Integer.class));

        performJson(put("/api/exams/{id}/standards/confirm", exam.examId()));
        JsonNode brokenBatch = upload(exam.examId(), zip(Map.of(
                "班级/20004_丁/20004_丁.docx", docx("没有题号"))));
        mockMvc.perform(post("/api/answer-import/batches/{id}/confirm-import-and-grade",
                        brokenBatch.get("id").longValue()))
                .andExpect(status().isConflict());
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from exam_submissions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from grading_tasks", Integer.class));
    }

    @Test
    void examWithAnswerImportHistoryCannotBeDeleted() throws Exception {
        ExamFixture exam = createChoiceOnlyExam();
        upload(exam.examId(), zip(Map.of(
                "班级/20005_戊/20005_戊.docx", docx("1. A"))));

        mockMvc.perform(delete("/api/exams/{id}", exam.examId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("该试卷已有答卷导入记录，不能直接删除"));
    }

    private ExamFixture createExam() throws Exception {
        JsonNode exam = performJson(post("/api/exams").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"批量导入测试","questions":[
                  {"questionNo":1,"questionType":"CHOICE","content":"选择1","maxScore":5,
                   "referenceAnswer":"A","rubricItems":[]},
                  {"questionNo":2,"questionType":"CHOICE","content":"选择2","maxScore":5,
                   "referenceAnswer":"B","rubricItems":[]},
                  {"questionNo":3,"questionType":"SHORT_ANSWER","content":"简答","maxScore":10,
                   "referenceAnswer":"参考","gradingCriteria":"按内容评分",
                   "rubricItems":[
                     {"itemOrder":1,"name":"概念","maxScore":3},
                     {"itemOrder":2,"name":"分析","maxScore":3},
                     {"itemOrder":3,"name":"结论","maxScore":4}
                   ]},
                  {"questionNo":4,"questionType":"PROGRAMMING","content":"编程","maxScore":10,
                   "referenceAnswer":"参考代码","rubricItems":[]}
                ]}
                """));
        long[] ids = new long[4];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = exam.get("questions").get(index).get("id").longValue();
        }
        return new ExamFixture(exam.get("id").longValue(), ids);
    }

    private ExamFixture createChoiceOnlyExam() throws Exception {
        JsonNode exam = performJson(post("/api/exams").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"一键评分测试","questions":[
                  {"questionNo":1,"questionType":"CHOICE","content":"请选择 A","maxScore":2,
                   "referenceAnswer":"A","rubricItems":[]}
                ]}
                """));
        return new ExamFixture(exam.get("id").longValue(),
                new long[]{exam.get("questions").get(0).get("id").longValue()});
    }

    private JsonNode upload(long examId, byte[] zip) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/exams/{id}/answer-import/batches/preview", examId)
                        .file(zipFile(zip)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode performJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private MockMultipartFile zipFile(byte[] bytes) {
        return new MockMultipartFile("file", "class-answers.zip", "application/zip", bytes);
    }

    private byte[] completeDocx(String choice1, String choice2, String shortAnswer, String program) throws Exception {
        return docx("1. " + choice1, "2. " + choice2, "1. " + shortAnswer, "1. " + program);
    }

    private byte[] docx(String... paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                document.createParagraph().createRun().setText(text);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (entry.getValue() != null) {
                    zip.write(entry.getValue());
                }
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private String correctionBody(JsonNode student, String studentNo, String studentName,
                                  long[] questionIds, String firstAnswer) {
        StringBuilder answers = new StringBuilder();
        for (int index = 0; index < student.get("answers").size(); index++) {
            if (index > 0) {
                answers.append(',');
            }
            answers.append("{\"answerId\":").append(student.get("answers").get(index).get("id").longValue())
                    .append(",\"questionId\":").append(questionIds[index])
                    .append(",\"answerText\":\"")
                    .append(index == 0 ? firstAnswer : student.get("answers").get(index).get("rawAnswer").stringValue())
                    .append("\"}");
        }
        return "{\"studentNo\":\"" + studentNo + "\",\"studentName\":\"" + studentName
                + "\",\"expectedVersion\":" + student.get("version").longValue()
                + ",\"answers\":[" + answers + "]}";
    }

    private String submissionBody(long[] questionIds, String studentNo, String studentName) {
        StringBuilder answers = new StringBuilder();
        for (int index = 0; index < questionIds.length; index++) {
            if (index > 0) {
                answers.append(',');
            }
            answers.append("{\"questionId\":").append(questionIds[index])
                    .append(",\"answerText\":\"A\"}");
        }
        return "{\"studentNo\":\"" + studentNo + "\",\"studentName\":\"" + studentName
                + "\",\"answers\":[" + answers + "]}";
    }

    private JsonNode findStudent(JsonNode batch, String studentNo) {
        for (JsonNode student : batch.get("students")) {
            if (!student.get("studentNo").isNull() && studentNo.equals(student.get("studentNo").stringValue())) {
                return student;
            }
        }
        throw new AssertionError("student not found: " + studentNo);
    }

    private java.util.Set<String> issueCodes(JsonNode student) {
        java.util.Set<String> codes = new java.util.HashSet<>();
        student.get("issues").forEach(issue -> codes.add(issue.get("code").stringValue()));
        return codes;
    }

    private record ExamFixture(long examId, long[] questionIds) {
    }
}
