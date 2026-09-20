package com.example.ratingsystem.grading.ai;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.ratingsystem.grading.ai.AiSettingsDtos.AiConnectionTestView;
import static com.example.ratingsystem.grading.ai.AiSettingsDtos.AiSettingsRequest;
import static com.example.ratingsystem.grading.ai.AiSettingsDtos.AiSettingsView;

@RestController
@RequestMapping("/api/settings/ai")
public class AiSettingsController {

    private final AiSettingsService settingsService;
    private final AiConnectionTester connectionTester;

    public AiSettingsController(AiSettingsService settingsService, AiConnectionTester connectionTester) {
        this.settingsService = settingsService;
        this.connectionTester = connectionTester;
    }

    @GetMapping
    AiSettingsView getSettings() {
        return settingsService.getView();
    }

    @PutMapping
    AiSettingsView saveSettings(@Valid @RequestBody AiSettingsRequest request) {
        return settingsService.save(request);
    }

    @PostMapping("/test")
    AiConnectionTestView testConnection(@Valid @RequestBody AiSettingsRequest request) {
        connectionTester.test(settingsService.resolveForTest(request));
        return new AiConnectionTestView(true, "连接成功，当前 API Key 和模型可以正常使用");
    }
}
