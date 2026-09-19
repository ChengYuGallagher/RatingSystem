package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.answerimport.AnswerImportDtos.ImportQuestionType;
import com.example.ratingsystem.answerimport.AnswerImportDtos.ParseStatus;
import com.example.ratingsystem.grading.model.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "answer_import_answers")
class BatchImportAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_import_id", nullable = false)
    private BatchImportStudentEntity studentImport;

    private int answerOrder;
    private Long questionId;
    private Integer questionNo;

    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    private Integer sourceQuestionNo;

    @Enumerated(EnumType.STRING)
    private ImportQuestionType sourceQuestionType;

    @Column(columnDefinition = "TEXT")
    private String rawAnswer;

    private Integer sourceStartBlock;
    private Integer sourceEndBlock;

    @Enumerated(EnumType.STRING)
    private ParseStatus parseStatus;

    protected BatchImportAnswerEntity() {
    }

    BatchImportAnswerEntity(int answerOrder, Long questionId, Integer questionNo, QuestionType questionType,
                            Integer sourceQuestionNo, ImportQuestionType sourceQuestionType, String rawAnswer,
                            Integer sourceStartBlock, Integer sourceEndBlock, ParseStatus parseStatus) {
        this.answerOrder = answerOrder;
        this.questionId = questionId;
        this.questionNo = questionNo;
        this.questionType = questionType;
        this.sourceQuestionNo = sourceQuestionNo;
        this.sourceQuestionType = sourceQuestionType;
        this.rawAnswer = rawAnswer;
        this.sourceStartBlock = sourceStartBlock;
        this.sourceEndBlock = sourceEndBlock;
        this.parseStatus = parseStatus;
    }

    void setStudentImport(BatchImportStudentEntity studentImport) {
        this.studentImport = studentImport;
    }

    void correct(Long questionId, int questionNo, QuestionType questionType, String rawAnswer) {
        this.questionId = questionId;
        this.questionNo = questionNo;
        this.questionType = questionType;
        this.rawAnswer = rawAnswer;
        this.parseStatus = ParseStatus.SUCCESS;
    }

    Long getId() {
        return id;
    }

    int getAnswerOrder() {
        return answerOrder;
    }

    Long getQuestionId() {
        return questionId;
    }

    Integer getQuestionNo() {
        return questionNo;
    }

    QuestionType getQuestionType() {
        return questionType;
    }

    Integer getSourceQuestionNo() {
        return sourceQuestionNo;
    }

    ImportQuestionType getSourceQuestionType() {
        return sourceQuestionType;
    }

    String getRawAnswer() {
        return rawAnswer;
    }

    Integer getSourceStartBlock() {
        return sourceStartBlock;
    }

    Integer getSourceEndBlock() {
        return sourceEndBlock;
    }

    ParseStatus getParseStatus() {
        return parseStatus;
    }
}
