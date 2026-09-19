package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.answerimport.AnswerImportDtos.ParseStatus;
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
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "answer_import_students")
class BatchImportStudentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private BatchImportEntity batch;

    @Column(nullable = false, length = SafeZipReader.MAX_PATH_LENGTH)
    private String sourcePath;
    private String detectedStudentNo;
    private String detectedStudentName;
    private String studentNo;
    private String studentName;
    private int expectedQuestionCount;
    private int recognizedQuestionCount;

    @Enumerated(EnumType.STRING)
    private ParseStatus parseStatus;

    @Enumerated(EnumType.STRING)
    private BatchReviewStatus reviewStatus = BatchReviewStatus.PENDING;

    private Long submissionId;

    @Version
    private long version;

    @OneToMany(mappedBy = "studentImport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("answerOrder ASC")
    private List<BatchImportAnswerEntity> answers = new ArrayList<>();

    protected BatchImportStudentEntity() {
    }

    BatchImportStudentEntity(String sourcePath, String detectedStudentNo, String detectedStudentName,
                             int expectedQuestionCount, int recognizedQuestionCount, ParseStatus parseStatus) {
        this.sourcePath = sourcePath;
        this.detectedStudentNo = detectedStudentNo;
        this.detectedStudentName = detectedStudentName;
        this.studentNo = detectedStudentNo;
        this.studentName = detectedStudentName;
        this.expectedQuestionCount = expectedQuestionCount;
        this.recognizedQuestionCount = recognizedQuestionCount;
        this.parseStatus = parseStatus;
    }

    void setBatch(BatchImportEntity batch) {
        this.batch = batch;
    }

    void addAnswer(BatchImportAnswerEntity answer) {
        answers.add(answer);
        answer.setStudentImport(this);
    }

    void markNeedsReview() {
        if (parseStatus == ParseStatus.SUCCESS) {
            parseStatus = ParseStatus.NEEDS_REVIEW;
        }
    }

    void correctIdentity(String studentNo, String studentName) {
        this.studentNo = studentNo;
        this.studentName = studentName;
    }

    void confirm() {
        reviewStatus = BatchReviewStatus.CONFIRMED;
    }

    void imported(Long submissionId) {
        this.submissionId = submissionId;
        reviewStatus = BatchReviewStatus.IMPORTED;
    }

    Long getId() {
        return id;
    }

    BatchImportEntity getBatch() {
        return batch;
    }

    String getSourcePath() {
        return sourcePath;
    }

    String getDetectedStudentNo() {
        return detectedStudentNo;
    }

    String getDetectedStudentName() {
        return detectedStudentName;
    }

    String getStudentNo() {
        return studentNo;
    }

    String getStudentName() {
        return studentName;
    }

    int getExpectedQuestionCount() {
        return expectedQuestionCount;
    }

    int getRecognizedQuestionCount() {
        return recognizedQuestionCount;
    }

    ParseStatus getParseStatus() {
        return parseStatus;
    }

    BatchReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    Long getSubmissionId() {
        return submissionId;
    }

    long getVersion() {
        return version;
    }

    List<BatchImportAnswerEntity> getAnswers() {
        return answers;
    }
}
