package com.example.ratingsystem.persistence;

import com.example.ratingsystem.grading.model.FillBlankGradingMode;
import com.example.ratingsystem.grading.model.QuestionType;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "questions")
class QuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    private int questionNo;

    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @Column(columnDefinition = "TEXT")
    private String content;

    private BigDecimal maxScore;

    @Column(columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(columnDefinition = "TEXT")
    private String gradingCriteria;

    @Enumerated(EnumType.STRING)
    private FillBlankGradingMode fillBlankGradingMode;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemOrder ASC")
    private Set<QuestionRubricItemEntity> rubricItems = new LinkedHashSet<>();

    protected QuestionEntity() {
    }

    QuestionEntity(int questionNo, QuestionType questionType, String content, BigDecimal maxScore,
                   String referenceAnswer, String gradingCriteria, FillBlankGradingMode fillBlankGradingMode) {
        this.questionNo = questionNo;
        this.questionType = questionType;
        this.content = content;
        this.maxScore = maxScore;
        this.referenceAnswer = referenceAnswer;
        this.gradingCriteria = gradingCriteria;
        this.fillBlankGradingMode = fillBlankGradingMode;
    }

    void setExam(ExamEntity exam) {
        this.exam = exam;
    }

    void addRubricItem(QuestionRubricItemEntity item) {
        rubricItems.add(item);
        item.setQuestion(this);
    }

    Long getId() {
        return id;
    }

    ExamEntity getExam() {
        return exam;
    }

    int getQuestionNo() {
        return questionNo;
    }

    QuestionType getQuestionType() {
        return questionType;
    }

    String getContent() {
        return content;
    }

    BigDecimal getMaxScore() {
        return maxScore;
    }

    String getReferenceAnswer() {
        return referenceAnswer;
    }

    String getGradingCriteria() {
        return gradingCriteria;
    }

    FillBlankGradingMode getFillBlankGradingMode() {
        return fillBlankGradingMode;
    }

    Set<QuestionRubricItemEntity> getRubricItems() {
        return rubricItems;
    }
}
