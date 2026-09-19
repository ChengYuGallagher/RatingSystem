package com.example.ratingsystem.persistence;

import com.example.ratingsystem.grading.model.FillBlankGradingMode;
import com.example.ratingsystem.grading.model.QuestionType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.ratingsystem.persistence.PersistenceDtos.AnswerInput;
import static com.example.ratingsystem.persistence.PersistenceDtos.CreateExamRequest;
import static com.example.ratingsystem.persistence.PersistenceDtos.CreateSubmissionRequest;
import static com.example.ratingsystem.persistence.PersistenceDtos.ExamView;
import static com.example.ratingsystem.persistence.PersistenceDtos.QuestionInput;
import static com.example.ratingsystem.persistence.PersistenceDtos.QuestionView;
import static com.example.ratingsystem.persistence.PersistenceDtos.RubricItemView;
import static com.example.ratingsystem.persistence.PersistenceDtos.SubmissionView;

@Service
public class ExamPersistenceService {

    private final ExamJpaRepository examRepository;
    private final StudentJpaRepository studentRepository;
    private final ExamSubmissionJpaRepository submissionRepository;

    public ExamPersistenceService(ExamJpaRepository examRepository, StudentJpaRepository studentRepository,
                                  ExamSubmissionJpaRepository submissionRepository) {
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public ExamView createExam(CreateExamRequest request) {
        validateQuestions(request);
        ExamEntity exam = new ExamEntity(request.name().trim());
        for (QuestionInput input : request.questions()) {
            QuestionEntity question = new QuestionEntity(
                    input.questionNo(), input.questionType(), input.content().trim(), input.maxScore(),
                    input.referenceAnswer().trim(), trimToNull(input.gradingCriteria()), normalizeFillMode(input)
            );
            input.rubricItems().forEach(item -> question.addRubricItem(
                    new QuestionRubricItemEntity(item.itemOrder(), item.name().trim(), item.maxScore())
            ));
            exam.addQuestion(question);
        }
        return toExamView(examRepository.saveAndFlush(exam));
    }

    @Transactional
    public SubmissionView createSubmission(Long examId, CreateSubmissionRequest request) {
        ExamEntity exam = examRepository.findDetailedById(examId)
                .orElseThrow(() -> new PersistenceNotFoundException("考试不存在: " + examId));

        Map<Long, AnswerInput> answersByQuestion = request.answers().stream().collect(Collectors.toMap(
                AnswerInput::questionId,
                Function.identity(),
                (left, right) -> {
                    throw new PersistenceValidationException("同一道题不能重复提交答案");
                }
        ));
        Set<Long> expectedQuestionIds = exam.getQuestions().stream().map(QuestionEntity::getId).collect(Collectors.toSet());
        if (!answersByQuestion.keySet().equals(expectedQuestionIds)) {
            throw new PersistenceValidationException("提交答案必须与考试题目集合完全一致，可用空字符串表示未作答");
        }

        StudentEntity student = studentRepository.findByStudentNo(request.studentNo().trim())
                .map(existing -> validateStudentName(existing, request.studentName()))
                .orElseGet(() -> studentRepository.save(
                        new StudentEntity(request.studentNo().trim(), request.studentName().trim())
                ));
        if (submissionRepository.existsByExamIdAndStudentId(examId, student.getId())) {
            throw new PersistenceConflictException("该考生已提交本次考试答案");
        }

        ExamSubmissionEntity submission = new ExamSubmissionEntity(exam, student);
        for (QuestionEntity question : exam.getQuestions()) {
            submission.addAnswer(new StudentAnswerEntity(
                    exam.getId(), question, answersByQuestion.get(question.getId()).answerText()
            ));
        }
        try {
            ExamSubmissionEntity saved = submissionRepository.saveAndFlush(submission);
            return new SubmissionView(saved.getId(), exam.getId(), student.getId(), student.getStudentNo(),
                    student.getName(), saved.getAnswers().size());
        } catch (DataIntegrityViolationException exception) {
            throw new PersistenceConflictException("该考生已提交本次考试答案");
        }
    }

    @Transactional(readOnly = true)
    public ExamView getExam(Long examId) {
        return examRepository.findDetailedById(examId)
                .map(this::toExamView)
                .orElseThrow(() -> new PersistenceNotFoundException("考试不存在: " + examId));
    }

    private void validateQuestions(CreateExamRequest request) {
        Set<Integer> questionNumbers = new HashSet<>();
        for (QuestionInput question : request.questions()) {
            if (!questionNumbers.add(question.questionNo())) {
                throw new PersistenceValidationException("题号不能重复: " + question.questionNo());
            }
            boolean aiQuestion = question.questionType() == QuestionType.SHORT_ANSWER
                    || (question.questionType() == QuestionType.FILL_BLANK
                    && question.fillBlankGradingMode() == FillBlankGradingMode.AI);
            if (aiQuestion && question.rubricItems().isEmpty()) {
                throw new PersistenceValidationException("AI 评分题必须提供结构化 rubricItems");
            }
            Set<Integer> itemOrders = new HashSet<>();
            BigDecimal rubricMax = BigDecimal.ZERO;
            for (PersistenceDtos.RubricItemInput item : question.rubricItems()) {
                if (!itemOrders.add(item.itemOrder())) {
                    throw new PersistenceValidationException("同一道题的评分点顺序不能重复");
                }
                rubricMax = rubricMax.add(item.maxScore());
            }
            if (!question.rubricItems().isEmpty() && rubricMax.compareTo(question.maxScore()) != 0) {
                throw new PersistenceValidationException("评分点满分之和必须等于题目满分");
            }
        }
    }

    private FillBlankGradingMode normalizeFillMode(QuestionInput input) {
        if (input.questionType() != QuestionType.FILL_BLANK) {
            return null;
        }
        return input.fillBlankGradingMode() == null ? FillBlankGradingMode.EXACT : input.fillBlankGradingMode();
    }

    private StudentEntity validateStudentName(StudentEntity student, String suppliedName) {
        if (!student.getName().equals(suppliedName.trim())) {
            throw new PersistenceConflictException("该学号已关联其他姓名，请核对考生信息");
        }
        return student;
    }

    private ExamView toExamView(ExamEntity exam) {
        return new ExamView(exam.getId(), exam.getName(), exam.getStatus(), exam.getQuestions().stream()
                .sorted(java.util.Comparator.comparingInt(QuestionEntity::getQuestionNo))
                .map(question -> new QuestionView(
                        question.getId(), question.getQuestionNo(), question.getQuestionType(), question.getMaxScore(),
                        question.getRubricItems().stream().map(item -> new RubricItemView(
                                item.getId(), item.getItemOrder(), item.getName(), item.getMaxScore()
                        )).toList()
                )).toList());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
