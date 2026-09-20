package com.example.ratingsystem.grading.web;

import com.example.ratingsystem.answerimport.AnswerImportException;
import com.example.ratingsystem.examimport.ExamPaperAiException;
import com.example.ratingsystem.examimport.ExamPaperImportException;
import com.example.ratingsystem.grading.ai.AiConnectionException;
import com.example.ratingsystem.grading.ai.AiSettingsException;
import com.example.ratingsystem.grading.service.InvalidGradingRequestException;
import com.example.ratingsystem.persistence.PersistenceConflictException;
import com.example.ratingsystem.persistence.PersistenceNotFoundException;
import com.example.ratingsystem.persistence.PersistenceValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GradingExceptionHandler {

    @ExceptionHandler(ExamPaperImportException.class)
    ProblemDetail handleExamPaperImport(ExamPaperImportException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getMessage());
        problem.setTitle("试卷草稿解析失败");
        return problem;
    }

    @ExceptionHandler(ExamPaperAiException.class)
    ProblemDetail handleExamPaperAi(ExamPaperAiException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                exception.getMessage());
        problem.setTitle("AI 试卷解析失败");
        return problem;
    }

    @ExceptionHandler(AiSettingsException.class)
    ProblemDetail handleAiSettings(AiSettingsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("AI 配置无法保存");
        return problem;
    }

    @ExceptionHandler(AiConnectionException.class)
    ProblemDetail handleAiConnection(AiConnectionException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("AI 连接测试失败");
        return problem;
    }

    @ExceptionHandler(AnswerImportException.class)
    ProblemDetail handleAnswerImport(AnswerImportException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("答卷导入请求不合法");
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "上传文件超过服务器允许的 50 MB 上限");
        problem.setTitle("上传文件过大");
        return problem;
    }

    @ExceptionHandler(InvalidGradingRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidGradingRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("评分请求不合法");
        return problem;
    }

    @ExceptionHandler(PersistenceValidationException.class)
    ProblemDetail handlePersistenceValidation(PersistenceValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("持久化请求不合法");
        return problem;
    }

    @ExceptionHandler(PersistenceNotFoundException.class)
    ProblemDetail handleNotFound(PersistenceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("资源不存在");
        return problem;
    }

    @ExceptionHandler(PersistenceConflictException.class)
    ProblemDetail handleConflict(PersistenceConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("请求与当前状态冲突");
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "评分结果已被其他审核操作修改，请重新查询后再提交"
        );
        problem.setTitle("审核版本冲突");
        return problem;
    }
}
