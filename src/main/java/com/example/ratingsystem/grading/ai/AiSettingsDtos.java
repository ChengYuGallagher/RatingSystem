package com.example.ratingsystem.grading.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AiSettingsDtos {

    private AiSettingsDtos() {
    }

    public record AiSettingsRequest(
            String apiKey,
            @NotBlank @Size(max = 1_000) String baseUrl,
            @NotBlank @Size(max = 200) String model
    ) {
    }

    public record AiSettingsView(
            boolean configured,
            boolean apiKeyConfigured,
            String baseUrl,
            String model,
            String source,
            Instant updatedAt,
            String statusMessage
    ) {
    }

    public record AiConnectionTestView(boolean success, String message) {
    }
}
