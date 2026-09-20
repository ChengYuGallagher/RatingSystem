package com.example.ratingsystem.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ExamCascadeDeletionRepository {

    private final JdbcTemplate jdbcTemplate;

    ExamCascadeDeletionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    int deleteExamGraph(Long examId) {
        jdbcTemplate.update("""
                delete from grading_task_items
                 where task_id in (select id from grading_tasks where exam_id = ?)
                """, examId);
        jdbcTemplate.update("delete from grading_tasks where exam_id = ?", examId);

        jdbcTemplate.update("""
                delete from grading_result_items
                 where grading_result_id in (
                       select gr.id
                         from grading_results gr
                         join student_answers sa on sa.id = gr.student_answer_id
                        where sa.exam_id = ?
                 )
                """, examId);
        jdbcTemplate.update("""
                delete from grading_results
                 where student_answer_id in (select id from student_answers where exam_id = ?)
                """, examId);

        jdbcTemplate.update("""
                delete from answer_import_issues
                 where batch_id in (select id from answer_import_batches where exam_id = ?)
                """, examId);
        jdbcTemplate.update("""
                delete from answer_import_answers
                 where student_import_id in (
                       select ais.id
                         from answer_import_students ais
                         join answer_import_batches aib on aib.id = ais.batch_id
                        where aib.exam_id = ?
                 )
                """, examId);
        jdbcTemplate.update("""
                delete from answer_import_students
                 where batch_id in (select id from answer_import_batches where exam_id = ?)
                """, examId);
        jdbcTemplate.update("delete from answer_import_batches where exam_id = ?", examId);

        jdbcTemplate.update("delete from student_answers where exam_id = ?", examId);
        jdbcTemplate.update("delete from exam_submissions where exam_id = ?", examId);

        jdbcTemplate.update("""
                delete from question_rubric_items
                 where question_id in (select id from questions where exam_id = ?)
                """, examId);
        jdbcTemplate.update("delete from questions where exam_id = ?", examId);
        return jdbcTemplate.update("delete from exams where id = ?", examId);
    }
}
