package com.example.ratingsystem.examimport;

import com.example.ratingsystem.grading.ai.AiClient;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamPaperImportServiceTests {

    @Test
    void extractsValidatedDraftAndMarksAiInferredAnswerForReview() throws Exception {
        String response = """
                {
                  "suggestedName":"Java 基础测试",
                  "issues":[],
                  "questions":[
                    {
                      "questionNo":1,"questionType":"CHOICE",
                      "content":"1. Java 中哪个关键字用于定义类？","maxScore":2,
                      "referenceAnswer":"A","answerSource":"REFERENCE_DOCUMENT",
                      "gradingCriteria":"答案一致得满分","fillBlankGradingMode":null,
                      "rubricItems":[],"needsReview":false,"reviewNotes":[]
                    },
                    {
                      "questionNo":2,"questionType":"SHORT_ANSWER",
                      "content":"2. 简述 Java 封装。","maxScore":3,
                      "referenceAnswer":"封装将数据和方法组织在类中。","answerSource":"AI_INFERRED",
                      "gradingCriteria":"根据概念准确性评分","fillBlankGradingMode":null,
                      "rubricItems":[
                        {"itemOrder":1,"name":"说明类的组织作用","maxScore":1},
                        {"itemOrder":2,"name":"说明访问控制","maxScore":2}
                      ],
                      "needsReview":false,"reviewNotes":[]
                    }
                  ]
                }
                """;
        ExamPaperImportService service = service(response);

        var draft = service.preview(
                docx("Java测试卷.docx", "Java 基础测试", "1. Java 中哪个关键字用于定义类？", "2. 简述 Java 封装。"),
                docx("参考答案.docx", "第1题答案：A")
        );

        assertEquals("Java 基础测试", draft.suggestedName());
        assertEquals(2, draft.questions().size());
        assertEquals("1. Java 中哪个关键字用于定义类？", draft.questions().get(0).content());
        assertEquals(ExamPaperImportDtos.AnswerSource.REFERENCE_DOCUMENT,
                draft.questions().get(0).answerSource());
        assertTrue(draft.questions().get(1).needsReview());
        assertEquals(ExamPaperImportDtos.AnswerSource.AI_INFERRED,
                draft.questions().get(1).answerSource());
        assertTrue(draft.questions().get(1).reviewNotes().stream()
                .anyMatch(note -> note.contains("AI 推导")));
        assertTrue(draft.needsReview());
    }

    @Test
    void rejectsInvalidJsonWithoutCreatingASeeminglyValidDraft() throws Exception {
        ExamPaperImportService service = service("not-json");

        ExamPaperImportException exception = assertThrows(ExamPaperImportException.class,
                () -> service.preview(docx("试卷.docx", "1. 原始题干"), null));

        assertTrue(exception.getMessage().contains("不是合法 JSON"));
    }

    @Test
    void rejectsQuestionTextThatWasRewrittenByAi() throws Exception {
        String response = """
                {"suggestedName":"测试","issues":[],"questions":[{
                  "questionNo":1,"questionType":"CHOICE","content":"AI 改写后的题干","maxScore":2,
                  "referenceAnswer":"A","answerSource":"AI_INFERRED","gradingCriteria":null,
                  "fillBlankGradingMode":null,"rubricItems":[],"needsReview":true,"reviewNotes":[]
                }]}
                """;
        ExamPaperImportService service = service(response);

        ExamPaperImportException exception = assertThrows(ExamPaperImportException.class,
                () -> service.preview(docx("试卷.docx", "1. 原始题干"), null));

        assertTrue(exception.getMessage().contains("并非来自原始试卷原文"));
    }

    @Test
    void rejectsNonSequentialQuestionsAndInvalidRubricTotals() throws Exception {
        List<String> responses = List.of(
                """
                {"suggestedName":"测试","issues":[],"questions":[{
                  "questionNo":2,"questionType":"CHOICE","content":"1. 原始题干","maxScore":2,
                  "referenceAnswer":"A","answerSource":"AI_INFERRED","gradingCriteria":null,
                  "fillBlankGradingMode":null,"rubricItems":[],"needsReview":true,"reviewNotes":[]
                }]}
                """,
                """
                {"suggestedName":"测试","issues":[],"questions":[{
                  "questionNo":1,"questionType":"SHORT_ANSWER","content":"1. 原始题干","maxScore":3,
                  "referenceAnswer":"候选答案","answerSource":"AI_INFERRED","gradingCriteria":"按点给分",
                  "fillBlankGradingMode":null,
                  "rubricItems":[{"itemOrder":1,"name":"评分点","maxScore":2}],
                  "needsReview":true,"reviewNotes":[]
                }]}
                """
        );

        for (String response : responses) {
            ExamPaperImportException exception = assertThrows(ExamPaperImportException.class,
                    () -> service(response).preview(docx("试卷.docx", "1. 原始题干"), null));
            assertTrue(exception.getMessage().contains("未创建试卷")
                    || exception.getMessage().contains("题号不连续"));
        }
    }

    @Test
    void rejectsDamagedOrTextlessDocx() throws Exception {
        ExamPaperImportService service = service("{}");
        MockMultipartFile damaged = new MockMultipartFile(
                "examFile", "试卷.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not-a-docx".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(ExamPaperImportException.class, () -> service.preview(damaged, null));
        assertThrows(ExamPaperImportException.class,
                () -> service.preview(docx("空试卷.docx"), null));
    }

    private ExamPaperImportService service(String response) {
        AiClient client = (systemPrompt, userPrompt) -> response;
        return new ExamPaperImportService(new ExamDocxTextExtractor(), client, new ObjectMapper());
    }

    private MockMultipartFile docx(String filename, String... paragraphs) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            for (String value : paragraphs) {
                document.createParagraph().createRun().setText(value);
            }
            document.write(output);
        }
        return new MockMultipartFile(
                "examFile", filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                output.toByteArray());
    }
}
