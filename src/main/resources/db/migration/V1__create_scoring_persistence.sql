CREATE TABLE exams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_exams_status CHECK (status IN ('DRAFT', 'SCORING'))
);

CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    question_no INT NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    max_score DECIMAL(8,2) NOT NULL,
    reference_answer TEXT NOT NULL,
    grading_criteria TEXT NULL,
    fill_blank_grading_mode VARCHAR(20) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_questions_exam_no UNIQUE (exam_id, question_no),
    CONSTRAINT uk_questions_id_exam UNIQUE (id, exam_id),
    CONSTRAINT fk_questions_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT chk_questions_no CHECK (question_no > 0),
    CONSTRAINT chk_questions_max_score CHECK (max_score > 0),
    CONSTRAINT chk_questions_type CHECK (question_type IN ('CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'SHORT_ANSWER')),
    CONSTRAINT chk_questions_fill_mode CHECK (fill_blank_grading_mode IS NULL OR fill_blank_grading_mode IN ('EXACT', 'AI'))
);

CREATE TABLE question_rubric_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    item_order INT NOT NULL,
    name VARCHAR(500) NOT NULL,
    max_score DECIMAL(8,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rubric_question_order UNIQUE (question_id, item_order),
    CONSTRAINT fk_rubric_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT chk_rubric_order CHECK (item_order > 0),
    CONSTRAINT chk_rubric_max_score CHECK (max_score > 0)
);

CREATE TABLE students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_no VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_students_student_no UNIQUE (student_no)
);

CREATE TABLE exam_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_submissions_exam_student UNIQUE (exam_id, student_id),
    CONSTRAINT uk_submissions_id_exam UNIQUE (id, exam_id),
    CONSTRAINT fk_submissions_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES students (id)
);

CREATE TABLE student_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    exam_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_text TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_answers_submission_question UNIQUE (submission_id, question_id),
    CONSTRAINT fk_answers_submission_exam FOREIGN KEY (submission_id, exam_id)
        REFERENCES exam_submissions (id, exam_id),
    CONSTRAINT fk_answers_question_exam FOREIGN KEY (question_id, exam_id)
        REFERENCES questions (id, exam_id)
);

CREATE TABLE grading_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_answer_id BIGINT NOT NULL,
    grading_status VARCHAR(20) NOT NULL,
    suggested_score DECIMAL(8,2) NULL,
    reason TEXT NULL,
    failure_message VARCHAR(1000) NULL,
    actual_score DECIMAL(8,2) NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 1,
    running_since TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_grading_answer UNIQUE (student_answer_id),
    CONSTRAINT fk_grading_answer FOREIGN KEY (student_answer_id) REFERENCES student_answers (id),
    CONSTRAINT chk_grading_status CHECK (grading_status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_grading_review_status CHECK (review_status IN ('PENDING', 'CONFIRMED')),
    CONSTRAINT chk_grading_attempt_count CHECK (attempt_count > 0),
    CONSTRAINT chk_grading_suggested_score CHECK (suggested_score IS NULL OR suggested_score >= 0),
    CONSTRAINT chk_grading_actual_score CHECK (actual_score IS NULL OR actual_score >= 0),
    CONSTRAINT chk_grading_review_score CHECK (
        (review_status = 'PENDING' AND actual_score IS NULL)
        OR (review_status = 'CONFIRMED' AND actual_score IS NOT NULL)
    ),
    CONSTRAINT chk_grading_success_score CHECK (
        (grading_status = 'SUCCESS' AND suggested_score IS NOT NULL)
        OR (grading_status IN ('RUNNING', 'FAILED') AND suggested_score IS NULL)
    )
);

CREATE TABLE grading_result_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grading_result_id BIGINT NOT NULL,
    rubric_item_id BIGINT NOT NULL,
    max_score DECIMAL(8,2) NOT NULL,
    suggested_score DECIMAL(8,2) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_result_rubric UNIQUE (grading_result_id, rubric_item_id),
    CONSTRAINT fk_result_items_result FOREIGN KEY (grading_result_id) REFERENCES grading_results (id),
    CONSTRAINT fk_result_items_rubric FOREIGN KEY (rubric_item_id) REFERENCES question_rubric_items (id),
    CONSTRAINT chk_result_item_max CHECK (max_score > 0),
    CONSTRAINT chk_result_item_score CHECK (suggested_score >= 0 AND suggested_score <= max_score)
);
