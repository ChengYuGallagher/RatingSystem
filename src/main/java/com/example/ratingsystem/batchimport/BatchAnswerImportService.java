package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.answerimport.AnswerImportDtos.AnswerImportPreview;
import com.example.ratingsystem.answerimport.AnswerImportDtos.ImportQuestionType;
import com.example.ratingsystem.answerimport.AnswerImportDtos.ImportStructureRequest;
import com.example.ratingsystem.answerimport.AnswerImportDtos.ParseStatus;
import com.example.ratingsystem.answerimport.AnswerImportDtos.SectionSpec;
import com.example.ratingsystem.answerimport.AnswerImportDtos.StudentIdentity;
import com.example.ratingsystem.answerimport.AnswerImportException;
import com.example.ratingsystem.answerimport.DocxAnswerImportService;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchAnswerCorrection;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchAnswerView;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchImportExecutionView;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchImportView;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchIssueView;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchStudentView;
import com.example.ratingsystem.batchimport.BatchImportDtos.ConfirmBatchStudentRequest;
import com.example.ratingsystem.batchimport.BatchImportDtos.ImportedSubmissionView;
import com.example.ratingsystem.batchimport.BatchImportDtos.UpdateBatchStudentRequest;
import com.example.ratingsystem.grading.model.QuestionType;
import com.example.ratingsystem.persistence.ExamPersistenceService;
import com.example.ratingsystem.persistence.PersistenceConflictException;
import com.example.ratingsystem.persistence.PersistenceDtos.ExamView;
import com.example.ratingsystem.persistence.PersistenceDtos.QuestionView;
import com.example.ratingsystem.persistence.PersistenceDtos.SubmissionView;
import com.example.ratingsystem.persistence.PersistenceNotFoundException;
import com.example.ratingsystem.persistence.PersistenceValidationException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BatchAnswerImportService {

    private final SafeZipReader zipReader;
    private final DocxAnswerImportService docxService;
    private final ExamPersistenceService examService;
    private final BatchImportJpaRepository batchRepository;
    private final BatchImportStudentJpaRepository studentRepository;
    private final BatchSubmissionImporter submissionImporter;
    private final EntityManager entityManager;

    public BatchAnswerImportService(SafeZipReader zipReader,
                                    DocxAnswerImportService docxService,
                                    ExamPersistenceService examService,
                                    BatchImportJpaRepository batchRepository,
                                    BatchImportStudentJpaRepository studentRepository,
                                    BatchSubmissionImporter submissionImporter,
                                    EntityManager entityManager) {
        this.zipReader = zipReader;
        this.docxService = docxService;
        this.examService = examService;
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.submissionImporter = submissionImporter;
        this.entityManager = entityManager;
    }

    @Transactional
    public BatchImportView preview(Long examId, MultipartFile archive) {
        ExamView exam = examService.getExam(examId);
        ExamLayout layout = buildLayout(exam);
        SafeZipReader.ArchiveContent archiveContent = zipReader.read(archive);
        BatchImportEntity batch = new BatchImportEntity(examId, cleanFilename(archive.getOriginalFilename()));
        archiveContent.issues().forEach(issue -> batch.addIssue(
                new BatchImportIssueEntity(null, null, issue.code(), issue.message())));

        Map<String, List<SafeZipReader.ArchiveDocx>> documentsByFolder = archiveContent.documents().stream()
                .collect(Collectors.groupingBy(document -> parentPath(document.path()),
                        LinkedHashMap::new, Collectors.toList()));
        Set<String> studentFolders = new LinkedHashSet<>(documentsByFolder.keySet());
        archiveContent.directories().stream()
                .filter(directory -> tryIdentify(leafName(directory) + ".docx") != null)
                .forEach(studentFolders::add);

        studentFolders.stream().sorted().forEach(folder -> {
            List<SafeZipReader.ArchiveDocx> documents = documentsByFolder.getOrDefault(folder, List.of());
            BatchImportStudentEntity student = documents.size() == 1
                    ? parseStudent(folder, documents.get(0), layout, batch)
                    : createInvalidFolderStudent(folder, documents, layout, batch);
            batch.addStudent(student);
        });

        markDuplicateStudentNumbers(batch);
        BatchImportEntity saved = batchRepository.saveAndFlush(batch);
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public BatchImportView getBatch(Long batchId) {
        return toView(findBatch(batchId));
    }

    @Transactional(readOnly = true)
    public BatchImportView getLatestBatch(Long examId) {
        examService.getExam(examId);
        BatchImportEntity batch = batchRepository.findFirstByExamIdOrderByIdDesc(examId)
                .orElseThrow(() -> new PersistenceNotFoundException("该考试尚无批量答卷导入记录"));
        return toView(batch);
    }

    @Transactional
    public BatchStudentView updateStudent(Long batchId, Long studentImportId,
                                          UpdateBatchStudentRequest request) {
        BatchImportStudentEntity student = findStudentForUpdate(batchId, studentImportId);
        requirePendingAndVersion(student, request.expectedVersion());
        ExamView exam = examService.getExam(student.getBatch().getExamId());
        Map<Long, QuestionView> questions = questionsById(exam);
        Map<Long, BatchImportAnswerEntity> storedAnswers = student.getAnswers().stream()
                .collect(Collectors.toMap(BatchImportAnswerEntity::getId, Function.identity()));
        if (request.answers().size() != storedAnswers.size()
                || !request.answers().stream().map(BatchAnswerCorrection::answerId)
                .collect(Collectors.toSet()).equals(storedAnswers.keySet())) {
            throw new PersistenceValidationException("修正请求必须包含该学生全部解析答案，且 answerId 不能重复或缺失");
        }

        Set<Long> correctedQuestionIds = new LinkedHashSet<>();
        for (BatchAnswerCorrection correction : request.answers()) {
            QuestionView question = questions.get(correction.questionId());
            if (question == null) {
                throw new PersistenceValidationException("题目不属于当前考试: " + correction.questionId());
            }
            if (!correctedQuestionIds.add(question.id())) {
                throw new PersistenceValidationException("同一道数据库题目不能映射多个答案: " + question.id());
            }
            storedAnswers.get(correction.answerId()).correct(
                    question.id(), question.questionNo(), question.questionType(), correction.answerText());
        }
        if (!correctedQuestionIds.equals(questions.keySet())) {
            throw new PersistenceValidationException("修正后的题目映射必须与考试题目集合完全一致");
        }
        student.correctIdentity(request.studentNo().trim(), request.studentName().trim());
        studentRepository.flush();
        return toStudentView(student, student.getBatch().getIssues());
    }

    @Transactional
    public BatchStudentView confirmStudent(Long batchId, Long studentImportId,
                                           ConfirmBatchStudentRequest request) {
        BatchImportStudentEntity student = findStudentForUpdate(batchId, studentImportId);
        requirePendingAndVersion(student, request.expectedVersion());
        validateReadyForConfirmation(student);
        if (studentRepository.existsByBatchIdAndStudentNoAndReviewStatusInAndIdNot(
                batchId, student.getStudentNo(),
                List.of(BatchReviewStatus.CONFIRMED, BatchReviewStatus.IMPORTED), student.getId())) {
            throw new PersistenceConflictException("同一批次已有相同学号的已确认答卷");
        }
        student.confirm();
        studentRepository.flush();
        return toStudentView(student, student.getBatch().getIssues());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BatchImportExecutionView importConfirmed(Long batchId) {
        if (!batchRepository.existsById(batchId)) {
            throw new PersistenceNotFoundException("批量导入任务不存在: " + batchId);
        }
        List<Long> confirmedIds = studentRepository
                .findByBatchIdAndReviewStatusOrderById(batchId, BatchReviewStatus.CONFIRMED)
                .stream().map(BatchImportStudentEntity::getId).toList();
        List<ImportedSubmissionView> imported = new ArrayList<>();
        List<BatchIssueView> failures = new ArrayList<>();
        for (Long studentId : confirmedIds) {
            try {
                SubmissionView submission = submissionImporter.importStudent(batchId, studentId);
                imported.add(new ImportedSubmissionView(studentId, submission.id(),
                        submission.studentNo(), submission.studentName()));
            } catch (RuntimeException exception) {
                String message = safeFailureMessage(exception);
                submissionImporter.recordFailure(batchId, studentId, message);
                failures.add(new BatchIssueView("FORMAL_IMPORT_FAILED", message, null));
            }
        }
        entityManager.clear();
        BatchImportEntity batch = findBatch(batchId);
        batch.completeIfAllImported();
        batchRepository.flush();
        int remaining = studentRepository
                .findByBatchIdAndReviewStatusOrderById(batchId, BatchReviewStatus.CONFIRMED).size();
        return new BatchImportExecutionView(batchId, imported.size(), remaining,
                List.copyOf(imported), List.copyOf(failures));
    }

    private BatchImportStudentEntity parseStudent(String folder, SafeZipReader.ArchiveDocx document,
                                                   ExamLayout layout, BatchImportEntity batch) {
        StudentIdentity folderIdentity = tryIdentify(leafName(folder) + ".docx");
        StudentIdentity fileIdentity = tryIdentify(leafName(document.path()));
        StudentIdentity detected = fileIdentity != null ? fileIdentity : folderIdentity;
        String parserFilename = detected == null ? "UNKNOWN_待确认.docx"
                : detected.studentNo() + "_" + detected.studentName() + ".docx";
        AnswerImportPreview preview;
        try {
            preview = docxService.preview(parserFilename, document.bytes(), layout.structure());
        } catch (AnswerImportException exception) {
            BatchImportStudentEntity failed = skeleton(document.path(), detected, layout, ParseStatus.FAILED);
            addIssue(batch, failed, null, "DOCX_PARSE_FAILED", exception.getMessage());
            return failed;
        }

        ParseStatus status = preview.parseStatus();
        BatchImportStudentEntity student = new BatchImportStudentEntity(
                document.path(), detected == null ? null : detected.studentNo(),
                detected == null ? null : detected.studentName(), preview.expectedQuestionCount(),
                preview.recognizedQuestionCount(), status);
        if (folderIdentity == null) {
            student.markNeedsReview();
            addIssue(batch, student, null, "INVALID_STUDENT_FOLDER_NAME",
                    "学生文件夹名必须符合 学号_姓名: " + folder);
        }
        if (fileIdentity == null) {
            student.markNeedsReview();
            addIssue(batch, student, null, "INVALID_DOCX_FILENAME",
                    "DOCX 文件名必须符合 学号_姓名.docx: " + document.path());
        }
        if (folderIdentity != null && fileIdentity != null
                && (!folderIdentity.studentNo().equals(fileIdentity.studentNo())
                || !folderIdentity.studentName().equals(fileIdentity.studentName()))) {
            student.markNeedsReview();
            addIssue(batch, student, null, "STUDENT_IDENTITY_MISMATCH",
                    "文件夹身份与 DOCX 文件名身份不一致");
        }

        Map<Long, QuestionView> questions = layout.questionsById();
        for (int index = 0; index < preview.answers().size(); index++) {
            var parsed = preview.answers().get(index);
            int answerOrder = index + 1;
            QuestionView question = parsed.questionId() == null ? null : questions.get(parsed.questionId());
            BatchImportAnswerEntity answer = new BatchImportAnswerEntity(
                    answerOrder, parsed.questionId(), question == null ? null : question.questionNo(),
                    question == null ? null : question.questionType(), parsed.questionNo(), parsed.questionType(),
                    parsed.rawAnswer(), parsed.sourceStartBlock(), parsed.sourceEndBlock(), parsed.parseStatus());
            student.addAnswer(answer);
            parsed.issues().forEach(issue -> addIssue(batch, student, answerOrder,
                    issue.code(), issue.message()));
        }
        preview.issues().forEach(issue -> addIssue(batch, student, null, issue.code(), issue.message()));
        preview.unassignedTextRanges().forEach(range -> addIssue(batch, student, null,
                "UNASSIGNED_TEXT", "原始块 " + range.sourceStartBlock() + "-" + range.sourceEndBlock()
                        + ": " + range.rawText()));
        return student;
    }

    private BatchImportStudentEntity createInvalidFolderStudent(String folder,
                                                                 List<SafeZipReader.ArchiveDocx> documents,
                                                                 ExamLayout layout,
                                                                 BatchImportEntity batch) {
        StudentIdentity identity = tryIdentify(leafName(folder) + ".docx");
        String sourcePath = documents.isEmpty() ? folder : folder + "/";
        BatchImportStudentEntity student = skeleton(sourcePath, identity, layout, ParseStatus.FAILED);
        if (documents.isEmpty()) {
            addIssue(batch, student, null, "DOCX_MISSING", "学生文件夹中没有 DOCX 答卷");
        } else {
            addIssue(batch, student, null, "MULTIPLE_DOCX_FILES",
                    "学生文件夹中存在多个 DOCX，无法确定正式答卷: "
                            + documents.stream().map(SafeZipReader.ArchiveDocx::path).toList());
        }
        return student;
    }

    private BatchImportStudentEntity skeleton(String sourcePath, StudentIdentity identity,
                                               ExamLayout layout, ParseStatus status) {
        BatchImportStudentEntity student = new BatchImportStudentEntity(
                sourcePath, identity == null ? null : identity.studentNo(),
                identity == null ? null : identity.studentName(), layout.orderedQuestions().size(), 0, status);
        for (int index = 0; index < layout.orderedQuestions().size(); index++) {
            LayoutQuestion item = layout.orderedQuestions().get(index);
            student.addAnswer(new BatchImportAnswerEntity(index + 1, item.question().id(),
                    item.question().questionNo(), item.question().questionType(), item.sourceQuestionNo(),
                    item.sourceType(), null, null, null, ParseStatus.FAILED));
        }
        return student;
    }

    private void markDuplicateStudentNumbers(BatchImportEntity batch) {
        Map<String, List<BatchImportStudentEntity>> byNumber = batch.getStudents().stream()
                .filter(student -> student.getStudentNo() != null)
                .collect(Collectors.groupingBy(BatchImportStudentEntity::getStudentNo));
        byNumber.values().stream().filter(students -> students.size() > 1).forEach(students ->
                students.forEach(student -> {
                    student.markNeedsReview();
                    addIssue(batch, student, null, "DUPLICATE_STUDENT_NO",
                            "同一 ZIP 中学号重复: " + student.getStudentNo());
                }));
    }

    private void validateReadyForConfirmation(BatchImportStudentEntity student) {
        if (student.getStudentNo() == null || student.getStudentNo().isBlank()
                || student.getStudentName() == null || student.getStudentName().isBlank()) {
            throw new PersistenceValidationException("必须先确认或修正学生学号和姓名");
        }
        Set<Long> expected = examService.getExam(student.getBatch().getExamId()).questions().stream()
                .map(QuestionView::id).collect(Collectors.toSet());
        Set<Long> actual = new LinkedHashSet<>();
        for (BatchImportAnswerEntity answer : student.getAnswers()) {
            if (answer.getQuestionId() == null || answer.getRawAnswer() == null) {
                throw new PersistenceValidationException("存在未映射题目或未确认答案文本，不能确认");
            }
            if (!actual.add(answer.getQuestionId())) {
                throw new PersistenceValidationException("存在重复题目映射，不能确认");
            }
        }
        if (!actual.equals(expected)) {
            throw new PersistenceValidationException("题目映射与考试题目集合不一致，不能确认");
        }
    }

    private void requirePendingAndVersion(BatchImportStudentEntity student, long expectedVersion) {
        if (student.getVersion() != expectedVersion) {
            throw new PersistenceConflictException("解析审核记录已被修改，请重新查询后再提交");
        }
        if (student.getReviewStatus() != BatchReviewStatus.PENDING) {
            throw new PersistenceConflictException("已确认或已导入的解析结果不能继续修改");
        }
    }

    private BatchImportStudentEntity findStudentForUpdate(Long batchId, Long studentImportId) {
        return studentRepository.findForUpdate(batchId, studentImportId)
                .orElseThrow(() -> new PersistenceNotFoundException("批量导入学生记录不存在: " + studentImportId));
    }

    private BatchImportEntity findBatch(Long batchId) {
        return batchRepository.findDetailedById(batchId)
                .orElseThrow(() -> new PersistenceNotFoundException("批量导入任务不存在: " + batchId));
    }

    private ExamLayout buildLayout(ExamView exam) {
        ExamQuestionIndex questionIndex = indexQuestions(exam);
        List<QuestionView> questions = questionIndex.orderedQuestions();
        List<SectionSpec> sections = new ArrayList<>();
        List<LayoutQuestion> ordered = new ArrayList<>();
        int cursor = 0;
        while (cursor < questions.size()) {
            QuestionType type = questions.get(cursor).questionType();
            ImportQuestionType importType = toImportType(type);
            List<Long> ids = new ArrayList<>();
            int localNumber = 1;
            while (cursor < questions.size() && questions.get(cursor).questionType() == type) {
                QuestionView question = questions.get(cursor++);
                ids.add(question.id());
                ordered.add(new LayoutQuestion(question, importType, localNumber++));
            }
            sections.add(new SectionSpec(importType, ids.size(), List.copyOf(ids)));
        }
        return new ExamLayout(new ImportStructureRequest(List.copyOf(sections)), List.copyOf(ordered),
                questionIndex.questionsById());
    }

    private ImportQuestionType toImportType(QuestionType type) {
        return ImportQuestionType.valueOf(type.name());
    }

    private Map<Long, QuestionView> questionsById(ExamView exam) {
        return indexQuestions(exam).questionsById();
    }

    private ExamQuestionIndex indexQuestions(ExamView exam) {
        Map<Long, QuestionView> questionsById = new LinkedHashMap<>();
        Set<Integer> questionNumbers = new HashSet<>();
        for (QuestionView question : exam.questions()) {
            if (question.id() == null) {
                throw new PersistenceValidationException("考试题目数据异常：存在未持久化的题目");
            }
            if (questionsById.putIfAbsent(question.id(), question) != null) {
                throw new PersistenceValidationException("考试题目数据异常：题目 ID 重复: " + question.id());
            }
            if (!questionNumbers.add(question.questionNo())) {
                throw new PersistenceValidationException("考试题目数据异常：题号重复: " + question.questionNo());
            }
        }
        List<QuestionView> orderedQuestions = questionsById.values().stream()
                .sorted(Comparator.comparingInt(QuestionView::questionNo)).toList();
        return new ExamQuestionIndex(orderedQuestions, Map.copyOf(questionsById));
    }

    private StudentIdentity tryIdentify(String filename) {
        try {
            return docxService.identify(filename);
        } catch (AnswerImportException exception) {
            return null;
        }
    }

    private void addIssue(BatchImportEntity batch, BatchImportStudentEntity student, Integer answerOrder,
                          String code, String message) {
        batch.addIssue(new BatchImportIssueEntity(student, answerOrder, code, message));
    }

    private String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private String leafName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private String cleanFilename(String filename) {
        return filename == null ? "batch.zip" : leafName(filename.replace('\\', '/'));
    }

    private String safeFailureMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "正式导入失败" : exception.getMessage();
    }

    private BatchImportView toView(BatchImportEntity batch) {
        List<BatchImportIssueEntity> issues = batch.getIssues();
        List<BatchStudentView> students = batch.getStudents().stream()
                .sorted(Comparator.comparing(BatchImportStudentEntity::getId))
                .map(student -> toStudentView(student, issues)).toList();
        int successful = (int) students.stream().filter(s -> s.parseStatus() == ParseStatus.SUCCESS).count();
        int needsReview = (int) students.stream().filter(s -> s.parseStatus() == ParseStatus.NEEDS_REVIEW).count();
        int failed = (int) students.stream().filter(s -> s.parseStatus() == ParseStatus.FAILED).count();
        int confirmed = (int) students.stream()
                .filter(s -> s.reviewStatus() == BatchReviewStatus.CONFIRMED).count();
        int imported = (int) students.stream()
                .filter(s -> s.reviewStatus() == BatchReviewStatus.IMPORTED).count();
        List<BatchIssueView> taskIssues = issues.stream().filter(issue -> issue.getStudentImport() == null)
                .map(this::toIssueView).toList();
        return new BatchImportView(batch.getId(), batch.getExamId(), batch.getOriginalFilename(), batch.getStatus(),
                students.size(), successful, needsReview, failed, confirmed, imported,
                students, taskIssues);
    }

    private BatchStudentView toStudentView(BatchImportStudentEntity student,
                                           List<BatchImportIssueEntity> allIssues) {
        List<BatchImportIssueEntity> studentIssues = allIssues.stream()
                .filter(issue -> issue.getStudentImport() != null
                        && issue.getStudentImport().getId().equals(student.getId()))
                .toList();
        Map<Integer, List<BatchIssueView>> answerIssues = studentIssues.stream()
                .filter(issue -> issue.getAnswerOrder() != null)
                .collect(Collectors.groupingBy(BatchImportIssueEntity::getAnswerOrder,
                        Collectors.mapping(this::toIssueView, Collectors.toList())));
        List<BatchAnswerView> answers = student.getAnswers().stream()
                .sorted(Comparator.comparingInt(BatchImportAnswerEntity::getAnswerOrder))
                .map(answer -> new BatchAnswerView(
                        answer.getId(), answer.getAnswerOrder(), answer.getQuestionId(), answer.getQuestionNo(),
                        answer.getQuestionType(), answer.getSourceQuestionNo(), answer.getSourceQuestionType(),
                        answer.getRawAnswer(), answer.getSourceStartBlock(), answer.getSourceEndBlock(),
                        answer.getParseStatus(), answerIssues.getOrDefault(answer.getAnswerOrder(), List.of())
                )).toList();
        return new BatchStudentView(student.getId(), student.getSourcePath(), student.getDetectedStudentNo(),
                student.getDetectedStudentName(), student.getStudentNo(), student.getStudentName(),
                student.getExpectedQuestionCount(), student.getRecognizedQuestionCount(), student.getParseStatus(),
                student.getReviewStatus(), student.getSubmissionId(), student.getVersion(), answers,
                studentIssues.stream().filter(issue -> issue.getAnswerOrder() == null)
                        .map(this::toIssueView).toList());
    }

    private BatchIssueView toIssueView(BatchImportIssueEntity issue) {
        return new BatchIssueView(issue.getCode(), issue.getMessage(), issue.getAnswerOrder());
    }

    private record LayoutQuestion(QuestionView question, ImportQuestionType sourceType, int sourceQuestionNo) {
    }

    private record ExamQuestionIndex(List<QuestionView> orderedQuestions,
                                     Map<Long, QuestionView> questionsById) {
    }

    private record ExamLayout(ImportStructureRequest structure, List<LayoutQuestion> orderedQuestions,
                              Map<Long, QuestionView> questionsById) {
    }
}
