package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.RatingSystemApplication;
import com.example.ratingsystem.grading.model.QuestionType;
import com.example.ratingsystem.persistence.ExamPersistenceService;
import com.example.ratingsystem.persistence.PersistenceDtos.CreateExamRequest;
import com.example.ratingsystem.persistence.PersistenceDtos.QuestionInput;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchImportRestartIntegrationTests {

    @Test
    void restoresBatchReviewDataAfterSpringBootRestart() throws Exception {
        String databaseBase = Path.of("target", "batch-restart-" + UUID.randomUUID()).toAbsolutePath().toString();
        String url = "jdbc:h2:file:" + databaseBase.replace('\\', '/')
                + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE";
        Long batchId;
        try {
            try (ConfigurableApplicationContext first = start(url, "create")) {
                ExamPersistenceService exams = first.getBean(ExamPersistenceService.class);
                long examId = exams.createExam(new CreateExamRequest("重启测试", List.of(
                        new QuestionInput(1, QuestionType.CHOICE, "题目", BigDecimal.ONE,
                                "A", null, null, List.of())
                ))).id();
                BatchAnswerImportService batches = first.getBean(BatchAnswerImportService.class);
                batchId = batches.preview(examId, new MockMultipartFile(
                        "file", "restart.zip", "application/zip", zipWithDocx())).id();
            }

            try (ConfigurableApplicationContext second = start(url, "validate")) {
                BatchImportDtos.BatchImportView restored = second.getBean(BatchAnswerImportService.class)
                        .getBatch(batchId);
                assertEquals(1, restored.uploadedStudentCount());
                assertEquals("30001", restored.students().get(0).studentNo());
                assertEquals("A", restored.students().get(0).answers().get(0).rawAnswer());
            }
        } finally {
            Files.deleteIfExists(Path.of(databaseBase + ".mv.db"));
            Files.deleteIfExists(Path.of(databaseBase + ".trace.db"));
        }
    }

    private ConfigurableApplicationContext start(String url, String ddlMode) {
        return new SpringApplicationBuilder(RatingSystemApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=" + ddlMode,
                        "--spring.flyway.enabled=false",
                        "--rating.ai.api-key="
                );
    }

    private byte[] zipWithDocx() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("1. A");
            document.write(output);
            docx = output.toByteArray();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("班级/30001_重启测试/30001_重启测试.docx"));
            zip.write(docx);
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        }
    }
}
