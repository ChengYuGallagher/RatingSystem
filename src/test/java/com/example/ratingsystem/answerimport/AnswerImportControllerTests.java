package com.example.ratingsystem.answerimport;

import com.example.ratingsystem.grading.web.GradingExceptionHandler;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;

class AnswerImportControllerTests {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DocxAnswerImportService service = new DocxAnswerImportService();
        mockMvc = MockMvcBuilders.standaloneSetup(new AnswerImportController(service))
                .setControllerAdvice(new GradingExceptionHandler())
                .build();
    }

    @Test
    void parsesAllQuestionTypesRepeatedNumbersAndMultilineAnswers() throws Exception {
        byte[] document = docx(
                "学生答卷",
                "一、选择题",
                "1. A",
                "2、B",
                "填空题",
                "1．不会",
                "判断题",
                "1）错误",
                "简答题",
                "1. 第一行",
                "第二行，保持原文。",
                "程序设计题",
                "1. public class Demo {",
                "    public static void main(String[] args) {",
                "        if (args.length < 2 && args[0] != null) {",
                "            System.out.println(\"<&>\");",
                "        }",
                "    }",
                "}"
        );

        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("244071101_张三.docx", document))
                        .file(structure("""
                                {"sections":[
                                  {"questionType":"CHOICE","questionCount":2,"questionIds":[101,102]},
                                  {"questionType":"FILL_BLANK","questionCount":1,"questionIds":[201]},
                                  {"questionType":"TRUE_FALSE","questionCount":1,"questionIds":[301]},
                                  {"questionType":"SHORT_ANSWER","questionCount":1,"questionIds":[401]},
                                  {"questionType":"PROGRAMMING","questionCount":1,"questionIds":[501]}
                                ]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentNo").value("244071101"))
                .andExpect(jsonPath("$.studentName").value("张三"))
                .andExpect(jsonPath("$.originalFilename").value("244071101_张三.docx"))
                .andExpect(jsonPath("$.expectedQuestionCount").value(6))
                .andExpect(jsonPath("$.recognizedQuestionCount").value(6))
                .andExpect(jsonPath("$.parseStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.answers[0].questionType").value("CHOICE"))
                .andExpect(jsonPath("$.answers[0].questionNo").value(1))
                .andExpect(jsonPath("$.answers[0].questionId").value(101))
                .andExpect(jsonPath("$.answers[0].rawAnswer").value("A"))
                .andExpect(jsonPath("$.answers[1].rawAnswer").value("B"))
                .andExpect(jsonPath("$.answers[2].questionType").value("FILL_BLANK"))
                .andExpect(jsonPath("$.answers[2].rawAnswer").value("不会"))
                .andExpect(jsonPath("$.answers[3].questionType").value("TRUE_FALSE"))
                .andExpect(jsonPath("$.answers[3].rawAnswer").value("错误"))
                .andExpect(jsonPath("$.answers[4].questionType").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.answers[4].rawAnswer").value("第一行\n第二行，保持原文。"))
                .andExpect(jsonPath("$.answers[5].questionType").value("PROGRAMMING"))
                .andExpect(jsonPath("$.answers[5].rawAnswer").value("""
                        public class Demo {
                            public static void main(String[] args) {
                                if (args.length < 2 && args[0] != null) {
                                    System.out.println("<&>");
                                }
                            }
                        }"""));
    }

    @Test
    void returnsNeedsReviewWhenQuestionIdsAreNotProvided() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("S-001_李四.docx", docx("1. A")))
                        .file(structure("""
                                {"sections":[{"questionType":"CHOICE","questionCount":1}]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.answers[0].questionId").doesNotExist())
                .andExpect(jsonPath("$.answers[0].parseStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.answers[0].issues[0].code").value("QUESTION_ID_UNMAPPED"))
                .andExpect(jsonPath("$.issues[0].code").value("QUESTION_IDS_UNMAPPED"));
    }

    @Test
    void reportsMissingQuestionWithoutShiftingLaterAnswer() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("10001_王五.docx", docx("1. A", "3. C")))
                        .file(structure("""
                                {"sections":[{"questionType":"CHOICE","questionCount":3,
                                  "questionIds":[11,12,13]}]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.recognizedQuestionCount").value(2))
                .andExpect(jsonPath("$.answers[0].rawAnswer").value("A"))
                .andExpect(jsonPath("$.answers[1].parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.answers[1].issues[0].code").value("QUESTION_NOT_FOUND"))
                .andExpect(jsonPath("$.answers[2].rawAnswer").value("C"));
    }

    @Test
    void marksExtraNumberedBlockAsAmbiguousInsteadOfSilentlyMerging() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("10002_赵六.docx", docx("1. 第一题", "1. 编号内容", "1. 最后一题")))
                        .file(structure("""
                                {"sections":[
                                  {"questionType":"SHORT_ANSWER","questionCount":1,"questionIds":[21]},
                                  {"questionType":"PROGRAMMING","questionCount":1,"questionIds":[22]}
                                ]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.issues[0].code").value("QUESTION_SEQUENCE_AMBIGUOUS"))
                .andExpect(jsonPath("$.answers[0].rawAnswer").value("第一题"))
                .andExpect(jsonPath("$.answers[0].parseStatus").value("NEEDS_REVIEW"));
    }

    @Test
    void readsQuestionAndAnswerFromTableRow() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("1");
            table.getRow(0).getCell(1).setText("A");
            table.getRow(1).getCell(0).setText("2");
            table.getRow(1).getCell(1).setText("不会");
            document.write(output);
            bytes = output.toByteArray();
        }

        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("10003_表格学生.docx", bytes))
                        .file(structure("""
                                {"sections":[{"questionType":"CHOICE","questionCount":2,
                                  "questionIds":[31,32]}]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.answers[0].rawAnswer").value("A"))
                .andExpect(jsonPath("$.answers[1].rawAnswer").value("不会"));
    }

    @Test
    void preservesNumberLikeLinesInsideFinalProgrammingAnswer() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("10007_代码学生.docx", docx(
                                "1. public class NumberedCode {",
                                "    int value = 0;",
                                "1. // 这是学生代码中的原始编号行",
                                "}"
                        )))
                        .file(structure("""
                                {"sections":[{"questionType":"PROGRAMMING","questionCount":1,
                                  "questionIds":[61]}]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.answers[0].rawAnswer").value("""
                        public class NumberedCode {
                            int value = 0;"""))
                .andExpect(jsonPath("$.unassignedTextRanges[0].rawText").value("""
                        1. // 这是学生代码中的原始编号行
                        }"""));
    }

    @Test
    void readsWordAutomaticNumberingWhenNumberIsNotPartOfParagraphText() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFNumbering numbering = document.createNumbering();
            CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
            abstractNum.setAbstractNumId(BigInteger.ZERO);
            CTLvl level = abstractNum.addNewLvl();
            level.setIlvl(BigInteger.ZERO);
            level.addNewStart().setVal(BigInteger.ONE);
            level.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
            level.addNewLvlText().setVal("%1.");
            BigInteger abstractId = numbering.addAbstractNum(new XWPFAbstractNum(abstractNum));
            BigInteger numberingId = numbering.addNum(abstractId);
            for (String answer : new String[]{"A", "B"}) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setNumID(numberingId);
                paragraph.setNumILvl(BigInteger.ZERO);
                paragraph.createRun().setText(answer);
            }
            document.write(output);
            bytes = output.toByteArray();
        }

        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("10008_自动编号.docx", bytes))
                        .file(structure("""
                                {"sections":[{"questionType":"CHOICE","questionCount":2,
                                  "questionIds":[71,72]}]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.answers[0].rawAnswer").value("A"))
                .andExpect(jsonPath("$.answers[1].rawAnswer").value("B"));
    }

    @Test
    void recognizesConfiguredFortyTwoQuestionExamWithoutHardcodedParserCounts() throws Exception {
        List<String> paragraphs = new ArrayList<>();
        addNumberedAnswers(paragraphs, 20, "选择答案");
        addNumberedAnswers(paragraphs, 10, "填空答案");
        addNumberedAnswers(paragraphs, 10, "判断答案");
        addNumberedAnswers(paragraphs, 1, "简答内容");
        addNumberedAnswers(paragraphs, 1, "不会");

        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("244071101_张三.docx", docx(paragraphs.toArray(String[]::new))))
                        .file(structure("""
                                {"sections":[
                                  {"questionType":"CHOICE","questionCount":20},
                                  {"questionType":"FILL_BLANK","questionCount":10},
                                  {"questionType":"TRUE_FALSE","questionCount":10},
                                  {"questionType":"SHORT_ANSWER","questionCount":1},
                                  {"questionType":"PROGRAMMING","questionCount":1}
                                ]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedQuestionCount").value(42))
                .andExpect(jsonPath("$.recognizedQuestionCount").value(42))
                .andExpect(jsonPath("$.answers[19].questionType").value("CHOICE"))
                .andExpect(jsonPath("$.answers[20].questionType").value("FILL_BLANK"))
                .andExpect(jsonPath("$.answers[30].questionType").value("TRUE_FALSE"))
                .andExpect(jsonPath("$.answers[40].questionType").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.answers[41].questionType").value("PROGRAMMING"))
                .andExpect(jsonPath("$.answers[41].rawAnswer").value("不会1"));
    }

    @Test
    void recoversTwoUnnumberedTrailingQuestionsFromBlankParagraphGroups() throws Exception {
        List<String> paragraphs = new ArrayList<>();
        addNumberedAnswers(paragraphs, 20, "选择答案");
        addNumberedAnswers(paragraphs, 10, "填空答案");
        addNumberedAnswers(paragraphs, 10, "X");
        paragraphs.add(null);
        paragraphs.add("答：这是完整的简答题内容，不会因为出现答字而改变边界");
        paragraphs.add(null);
        paragraphs.add("不会");

        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("20001_尾部无编号.docx", docx(paragraphs.toArray(String[]::new))))
                        .file(structure(javaExamStructure())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognizedQuestionCount").value(42))
                .andExpect(jsonPath("$.answers[39].rawAnswer").value("X10"))
                .andExpect(jsonPath("$.answers[39].sourceStartBlock").value(39))
                .andExpect(jsonPath("$.answers[39].sourceEndBlock").value(39))
                .andExpect(jsonPath("$.answers[40].questionType").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.answers[40].rawAnswer")
                        .value("答：这是完整的简答题内容，不会因为出现答字而改变边界"))
                .andExpect(jsonPath("$.answers[40].parseStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.answers[41].questionType").value("PROGRAMMING"))
                .andExpect(jsonPath("$.answers[41].rawAnswer").value("不会"))
                .andExpect(jsonPath("$.unassignedTextRanges", hasSize(0)));
    }

    @Test
    void preservesMultilineJavaAndDoesNotUseAnswerWordsAsBoundaries() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("20002_多行代码.docx", docx(
                                "1. X",
                                null,
                                "答：第一段中出现不会",
                                "第二段再次出现答：但仍属于简答题",
                                null,
                                "public class Demo {",
                                "    public static void main(String[] args) {",
                                "        System.out.println(\"不会 / 答：\");",
                                "    }",
                                "}"
                        )))
                        .file(structure("""
                                {"sections":[
                                  {"questionType":"TRUE_FALSE","questionCount":1,"questionIds":[1]},
                                  {"questionType":"SHORT_ANSWER","questionCount":1,"questionIds":[2]},
                                  {"questionType":"PROGRAMMING","questionCount":1,"questionIds":[3]}
                                ]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognizedQuestionCount").value(3))
                .andExpect(jsonPath("$.answers[0].rawAnswer").value("X"))
                .andExpect(jsonPath("$.answers[1].rawAnswer").value("""
                        答：第一段中出现不会
                        第二段再次出现答：但仍属于简答题"""))
                .andExpect(jsonPath("$.answers[2].rawAnswer").value("""
                        public class Demo {
                            public static void main(String[] args) {
                                System.out.println("不会 / 答：");
                            }
                        }"""));
    }

    @Test
    void keepsTrailingTextUnassignedWhenBlankParagraphBoundaryIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("20003_边界不明.docx", docx(
                                "1. X",
                                "答：这两段之间没有可靠结构边界",
                                "不会"
                        )))
                        .file(structure("""
                                {"sections":[
                                  {"questionType":"TRUE_FALSE","questionCount":1,"questionIds":[1]},
                                  {"questionType":"SHORT_ANSWER","questionCount":1,"questionIds":[2]},
                                  {"questionType":"PROGRAMMING","questionCount":1,"questionIds":[3]}
                                ]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.recognizedQuestionCount").value(1))
                .andExpect(jsonPath("$.answers[0].rawAnswer").value("X"))
                .andExpect(jsonPath("$.answers[1].parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.answers[2].parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.unassignedTextRanges[0].rawText").value("""
                        答：这两段之间没有可靠结构边界
                        不会"""));
    }

    @Test
    void parsesTheActualStudentDocumentWithJavaService() throws Exception {
        Path actualDocx = Path.of("test-files", "docx", "244071101_张三.docx");
        Assumptions.assumeTrue(Files.exists(actualDocx), "本地真实验收文件不存在时跳过");

        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("244071101_张三.docx", Files.readAllBytes(actualDocx)))
                        .file(structure(javaExamStructure())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentNo").value("244071101"))
                .andExpect(jsonPath("$.studentName").value("张三"))
                .andExpect(jsonPath("$.expectedQuestionCount").value(42))
                .andExpect(jsonPath("$.recognizedQuestionCount").value(42))
                .andExpect(jsonPath("$.sourceBlockCount").value(46))
                .andExpect(jsonPath("$.answers[39].rawAnswer").value("X"))
                .andExpect(jsonPath("$.answers[39].sourceStartBlock").value(41))
                .andExpect(jsonPath("$.answers[39].sourceEndBlock").value(41))
                .andExpect(jsonPath("$.answers[40].questionType").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.answers[40].questionNo").value(1))
                .andExpect(jsonPath("$.answers[40].rawAnswer").value(
                        "答：封装就是将同一类的属性行为之类的性质统一放在一个类中，后续的子类可以调用父类进行修改；"
                                + "继承就是子类可以用父类的一些方法、行为；多态就是子类可以有自己独特的行为"))
                .andExpect(jsonPath("$.answers[40].sourceStartBlock").value(43))
                .andExpect(jsonPath("$.answers[40].sourceEndBlock").value(43))
                .andExpect(jsonPath("$.answers[41].questionType").value("PROGRAMMING"))
                .andExpect(jsonPath("$.answers[41].questionNo").value(1))
                .andExpect(jsonPath("$.answers[41].rawAnswer").value("不会"))
                .andExpect(jsonPath("$.answers[41].sourceStartBlock").value(45))
                .andExpect(jsonPath("$.answers[41].sourceEndBlock").value(45))
                .andExpect(jsonPath("$.unassignedTextRanges", hasSize(0)));
    }

    @Test
    void returnsFailedPreviewWhenDocumentHasNoQuestionMarkers() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("10004_无编号.docx", docx("只有普通文字，没有题号")))
                        .file(structure("""
                                {"sections":[{"questionType":"CHOICE","questionCount":1,
                                  "questionIds":[41]}]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.recognizedQuestionCount").value(0))
                .andExpect(jsonPath("$.answers[0].parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.issues[*].code", hasItem("NO_QUESTION_RECOGNIZED")));
    }

    @Test
    void rejectsInvalidFilename() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("张三.docx", docx("1. A")))
                        .file(structure("""
                                {"sections":[{"questionType":"CHOICE","questionCount":1}]}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("答卷导入请求不合法"));
    }

    @Test
    void rejectsCorruptedDocx() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("10005_损坏文件.docx", "not-a-docx".getBytes()))
                        .file(structure("""
                                {"sections":[{"questionType":"CHOICE","questionCount":1}]}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("答卷导入请求不合法"));
    }

    @Test
    void rejectsQuestionIdCountMismatch() throws Exception {
        mockMvc.perform(multipart("/api/answer-import/preview")
                        .file(file("10006_配置错误.docx", docx("1. A", "2. B")))
                        .file(structure("""
                                {"sections":[{"questionType":"CHOICE","questionCount":2,
                                  "questionIds":[51]}]}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("答卷导入请求不合法"));
    }

    private MockMultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, DOCX_MEDIA_TYPE.toString(), content);
    }

    private MockMultipartFile structure(String json) {
        return new MockMultipartFile("structure", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
    }

    private byte[] docx(String... paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                XWPFParagraph created = document.createParagraph();
                if (paragraph != null) {
                    created.createRun().setText(paragraph);
                }
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private void addNumberedAnswers(List<String> paragraphs, int count, String answerPrefix) {
        for (int questionNo = 1; questionNo <= count; questionNo++) {
            paragraphs.add(questionNo + ". " + answerPrefix + questionNo);
        }
    }

    private String javaExamStructure() {
        return """
                {"sections":[
                  {"questionType":"CHOICE","questionCount":20},
                  {"questionType":"FILL_BLANK","questionCount":10},
                  {"questionType":"TRUE_FALSE","questionCount":10},
                  {"questionType":"SHORT_ANSWER","questionCount":1},
                  {"questionType":"PROGRAMMING","questionCount":1}
                ]}
                """;
    }
}
