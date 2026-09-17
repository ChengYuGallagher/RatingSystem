package com.example.ratingsystem.grading.web;

import com.example.ratingsystem.grading.service.InvalidGradingRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GradingExceptionHandler {

    @ExceptionHandler(InvalidGradingRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidGradingRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("评分请求不合法");
        return problem;
    }
}
