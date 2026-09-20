package com.example.ratingsystem.grading.ai;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

import static com.example.ratingsystem.grading.ai.AiSettingsDtos.AiSettingsRequest;
import static com.example.ratingsystem.grading.ai.AiSettingsDtos.AiSettingsView;
import static com.example.ratingsystem.grading.ai.EncryptedLocalAiSettingsStore.SavedAiSettings;

@Service
public class AiSettingsService implements AiConfigProvider {

    private final AiProperties environment;
    private final EncryptedLocalAiSettingsStore store;
    private volatile SavedAiSettings saved;
    private volatile String loadError;

    public AiSettingsService(AiProperties environment, AiSettingsProperties settingsProperties) {
        this.environment = environment;
        this.store = new EncryptedLocalAiSettingsStore(resolveDirectory(settingsProperties.directory()));
        try {
            SavedAiSettings loaded = store.load().orElse(null);
            if (loaded != null) {
                validateBaseUrl(loaded.baseUrl());
                normalizeModel(loaded.model());
            }
            this.saved = loaded;
        } catch (AiSettingsException exception) {
            this.loadError = exception.getMessage();
        }
    }

    @Override
    public AiRuntimeConfig current() {
        SavedAiSettings snapshot = saved;
        String apiKey = snapshot != null && StringUtils.hasText(snapshot.apiKey())
                ? snapshot.apiKey() : environment.apiKey();
        URI baseUrl = snapshot == null ? environment.baseUrl() : validateBaseUrl(snapshot.baseUrl());
        String model = snapshot == null ? environment.model() : snapshot.model();
        return new AiRuntimeConfig(apiKey, baseUrl, model, environment.maxTokens());
    }

    public AiSettingsView getView() {
        AiRuntimeConfig effective = current();
        SavedAiSettings snapshot = saved;
        String status;
        if (loadError != null) {
            status = loadError;
        } else if (effective.configured()) {
            status = snapshot == null ? "正在使用环境变量配置" : "正在使用网页保存的本机配置";
        } else {
            status = "AI 尚未完整配置";
        }
        return new AiSettingsView(
                effective.configured(), StringUtils.hasText(effective.apiKey()),
                effective.baseUrl() == null ? "" : effective.baseUrl().toString(),
                effective.model() == null ? "" : effective.model(),
                snapshot == null ? "ENVIRONMENT" : "SAVED_LOCAL",
                snapshot == null ? null : snapshot.updatedAt(), status
        );
    }

    public synchronized AiSettingsView save(AiSettingsRequest request) {
        String suppliedKey = normalizeKey(request.apiKey());
        if (loadError != null && suppliedKey.isEmpty()) {
            throw new AiSettingsException("原本机配置无法读取，请重新输入 API Key 后保存");
        }
        String keyToSave = suppliedKey;
        if (keyToSave.isEmpty() && saved != null) {
            keyToSave = saved.apiKey();
        }
        SavedAiSettings replacement = new SavedAiSettings(
                keyToSave,
                validateBaseUrl(request.baseUrl()).toString(),
                normalizeModel(request.model()),
                Instant.now()
        );
        store.save(replacement);
        saved = replacement;
        loadError = null;
        return getView();
    }

    public AiRuntimeConfig resolveForTest(AiSettingsRequest request) {
        String requestedKey = normalizeKey(request.apiKey());
        String effectiveKey = requestedKey.isEmpty() ? current().apiKey() : requestedKey;
        return new AiRuntimeConfig(
                effectiveKey,
                validateBaseUrl(request.baseUrl()),
                normalizeModel(request.model()),
                Math.min(environment.maxTokens(), 16)
        );
    }

    private URI validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return URI.create(uri.toString().replaceAll("/+$", ""));
        } catch (IllegalArgumentException exception) {
            throw new AiSettingsException("API Base URL 必须是有效的 HTTPS 地址");
        }
    }

    private String normalizeModel(String value) {
        String model = trimToEmpty(value);
        if (model.isEmpty()) {
            throw new AiSettingsException("模型名称不能为空");
        }
        return model;
    }

    private String normalizeKey(String value) {
        String apiKey = trimToEmpty(value);
        if (apiKey.length() > 500) {
            throw new AiSettingsException("API Key 长度不合法");
        }
        return apiKey;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static Path resolveDirectory(String configuredDirectory) {
        if (StringUtils.hasText(configuredDirectory)) {
            return Path.of(configuredDirectory.trim());
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (StringUtils.hasText(localAppData)) {
            return Path.of(localAppData, "RatingSystem");
        }
        return Path.of(System.getProperty("user.home"), ".ratingsystem");
    }
}
