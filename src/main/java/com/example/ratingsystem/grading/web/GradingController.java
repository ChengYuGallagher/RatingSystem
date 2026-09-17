package com.example.ratingsystem.grading.web;

import com.example.ratingsystem.grading.model.GradingRequest;
import com.example.ratingsystem.grading.model.GradingResult;
import com.example.ratingsystem.grading.service.GradingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grading")
public class GradingController {

    private final GradingService gradingService;

    public GradingController(GradingService gradingService) {
        this.gradingService = gradingService;
    }

    @PostMapping("/suggestions")
    public GradingResult grade(@Valid @RequestBody GradingRequest request) {
        return gradingService.grade(request);
    }
}
