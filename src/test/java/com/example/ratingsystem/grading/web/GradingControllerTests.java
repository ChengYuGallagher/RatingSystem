package com.example.ratingsystem.grading.web;

import com.example.ratingsystem.grading.ai.AiClient;
import com.example.ratingsystem.grading.ai.GradingResultValidator;
import com.example.ratingsystem.grading.service.AiGradingService;
import com.example.ratingsystem.grading.service.ExactMatchGrader;
import com.example.ratingsystem.grading.service.GradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GradingControllerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiClient forbiddenAiClient = (systemPrompt, userPrompt) -> {
            throw new AssertionError("本测试不应调用 AI");
        };
        ObjectMapper objectMapper = new ObjectMapper();
        AiGradingService aiGradingService = new AiGradingService(
                forbiddenAiClient,
                new GradingResultValidator(objectMapper),
                objectMapper
        );
        GradingService gradingService = new GradingService(new ExactMatchGrader(), aiGradingService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new GradingController(gradingService))
                .setControllerAdvice(new GradingExceptionHandler())
                .build();
    }

    @Test
    void returnsUnifiedPendingResultForChoiceQuestion() throws Exception {
        mockMvc.perform(post("/api/grading/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionId":3,
                                  "questionType":"CHOICE",
                                  "question":"请选择正确答案",
                                  "maxScore":2,
                                  "referenceAnswer":"B",
                                  "studentAnswer":"b"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(3))
                .andExpect(jsonPath("$.suggestedScore").value(2))
                .andExpect(jsonPath("$.actualScore").doesNotExist())
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"));
    }

    @Test
    void rejectsAiQuestionWithoutGradingCriteria() throws Exception {
        mockMvc.perform(post("/api/grading/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionId":4,
                                  "questionType":"SHORT_ANSWER",
                                  "question":"请简述概念",
                                  "maxScore":10,
                                  "referenceAnswer":"参考答案",
                                  "studentAnswer":"学生答案"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("评分请求不合法"));
    }

    @Test
    void rejectsZeroMaxScore() throws Exception {
        mockMvc.perform(post("/api/grading/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionId":5,
                                  "questionType":"CHOICE",
                                  "question":"请选择正确答案",
                                  "maxScore":0,
                                  "referenceAnswer":"A",
                                  "studentAnswer":"A"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
