ALTER TABLE questions DROP CONSTRAINT chk_questions_type;
ALTER TABLE questions ADD CONSTRAINT chk_questions_type
    CHECK (question_type IN ('CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'SHORT_ANSWER', 'PROGRAMMING'));

CREATE TABLE answer_import_batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REVIEWING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_import_batches_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT chk_import_batches_status CHECK (status IN ('REVIEWING', 'COMPLETED'))
);

CREATE TABLE answer_import_students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    source_path VARCHAR(700) NOT NULL,
    detected_student_no VARCHAR(100) NULL,
    detected_student_name VARCHAR(100) NULL,
    student_no VARCHAR(100) NULL,
    student_name VARCHAR(100) NULL,
    expected_question_count INT NOT NULL,
    recognized_question_count INT NOT NULL,
    parse_status VARCHAR(20) NOT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submission_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_import_student_source UNIQUE (batch_id, source_path),
    CONSTRAINT fk_import_students_batch FOREIGN KEY (batch_id) REFERENCES answer_import_batches (id),
    CONSTRAINT fk_import_students_submission FOREIGN KEY (submission_id) REFERENCES exam_submissions (id),
    CONSTRAINT chk_import_students_counts CHECK (
        expected_question_count >= 0 AND recognized_question_count >= 0
        AND recognized_question_count <= expected_question_count
    ),
    CONSTRAINT chk_import_students_parse CHECK (parse_status IN ('SUCCESS', 'NEEDS_REVIEW', 'FAILED')),
    CONSTRAINT chk_import_students_review CHECK (review_status IN ('PENDING', 'CONFIRMED', 'IMPORTED'))
);

CREATE TABLE answer_import_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_import_id BIGINT NOT NULL,
    answer_order INT NOT NULL,
    question_id BIGINT NULL,
    question_no INT NULL,
    question_type VARCHAR(30) NULL,
    source_question_no INT NULL,
    source_question_type VARCHAR(30) NULL,
    raw_answer TEXT NULL,
    source_start_block INT NULL,
    source_end_block INT NULL,
    parse_status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_import_answer_order UNIQUE (student_import_id, answer_order),
    CONSTRAINT fk_import_answers_student FOREIGN KEY (student_import_id) REFERENCES answer_import_students (id),
    CONSTRAINT fk_import_answers_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT chk_import_answers_order CHECK (answer_order > 0),
    CONSTRAINT chk_import_answers_parse CHECK (parse_status IN ('SUCCESS', 'NEEDS_REVIEW', 'FAILED'))
);

CREATE TABLE answer_import_issues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    student_import_id BIGINT NULL,
    answer_order INT NULL,
    code VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_import_issues_batch FOREIGN KEY (batch_id) REFERENCES answer_import_batches (id),
    CONSTRAINT fk_import_issues_student FOREIGN KEY (student_import_id) REFERENCES answer_import_students (id)
);
