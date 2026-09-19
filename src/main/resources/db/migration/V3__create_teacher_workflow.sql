ALTER TABLE exams ADD COLUMN standards_reviewed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exams ADD COLUMN standards_reviewed_at TIMESTAMP(6) NULL;

CREATE TABLE grading_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    processed_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grading_tasks_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT chk_grading_tasks_status CHECK (
        status IN ('RUNNING', 'COMPLETED', 'PARTIAL_FAILED')
    ),
    CONSTRAINT chk_grading_tasks_counts CHECK (
        total_count >= 0 AND processed_count >= 0 AND success_count >= 0 AND failed_count >= 0
        AND processed_count <= total_count
        AND success_count + failed_count = processed_count
    )
);

CREATE INDEX idx_grading_tasks_exam ON grading_tasks (exam_id, id);

CREATE TABLE grading_task_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_grading_task_submission UNIQUE (task_id, submission_id),
    CONSTRAINT fk_grading_task_items_task FOREIGN KEY (task_id) REFERENCES grading_tasks (id),
    CONSTRAINT fk_grading_task_items_submission FOREIGN KEY (submission_id) REFERENCES exam_submissions (id),
    CONSTRAINT chk_grading_task_items_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')
    ),
    CONSTRAINT chk_grading_task_items_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX idx_grading_task_items_status ON grading_task_items (task_id, status, id);
