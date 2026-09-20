package com.example.ratingsystem.grading.ai;

import com.example.ratingsystem.grading.web.GradingExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;

import static com.example.ratingsystem.grading.ai.AiSettingsDtos.AiSettingsRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiSettingsTests {

    @TempDir
    Path tempDir;

    @Test
    void savesEncryptedSettingsAndRestoresThemAfterRestart() throws Exception {
        Path settingsDirectory = tempDir.resolve("settings");
        AiSettingsService service = service(settingsDirectory, "environment-key");

        service.save(new AiSettingsRequest(
                "saved-secret-key", "https://api.example.test", "saved-model"));

        Path settingsFile = settingsDirectory.resolve("ai-settings.properties");
        Path keyFile = settingsDirectory.resolve("ai-settings.key");
        assertTrue(Files.exists(settingsFile));
        assertTrue(Files.exists(keyFile));
        assertFalse(Files.readString(settingsFile).contains("saved-secret-key"));
        assertFalse(Files.readString(settingsFile).contains("environment-key"));
        assertOwnerOnly(settingsDirectory, settingsFile, keyFile);

        AiSettingsService restarted = service(settingsDirectory, "different-environment-key");
        assertEquals("saved-secret-key", restarted.current().apiKey());
        assertEquals(URI.create("https://api.example.test"), restarted.current().baseUrl());
        assertEquals("saved-model", restarted.current().model());
        assertEquals("SAVED_LOCAL", restarted.getView().source());
    }

    @Test
    void blankKeyKeepsExistingSavedKey() {
        AiSettingsService service = service(tempDir.resolve("settings"), "environment-key");
        service.save(new AiSettingsRequest("first-key", "https://api.example.test", "first-model"));

        service.save(new AiSettingsRequest(" ", "https://api.changed.test", "changed-model"));

        assertEquals("first-key", service.current().apiKey());
        assertEquals(URI.create("https://api.changed.test"), service.current().baseUrl());
        assertEquals("changed-model", service.current().model());
    }

    @Test
    void deepSeekClientReadsNewSavedKeyForNewRequests() {
        AiSettingsService service = service(tempDir.resolve("settings"), "");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAiClient client = new DeepSeekAiClient(builder.build(), service);

        service.save(new AiSettingsRequest("first-key", "https://api.example.test", "model-a"));
        server.expect(once(), requestTo("https://api.example.test/chat/completions"))
                .andExpect(header("Authorization", "Bearer first-key"))
                .andRespond(successResponse());
        server.expect(once(), requestTo("https://api.example.test/chat/completions"))
                .andExpect(header("Authorization", "Bearer second-key"))
                .andRespond(successResponse());

        client.complete("system", "user");
        service.save(new AiSettingsRequest("second-key", "https://api.example.test", "model-b"));
        client.complete("system", "user");

        server.verify();
    }

    @Test
    void settingsApiNeverReturnsTheSavedKey() throws Exception {
        AiSettingsService service = service(tempDir.resolve("settings"), "");
        AiConnectionTester tester = mock(AiConnectionTester.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiSettingsController(service, tester)).build();

        String response = mockMvc.perform(put("/api/settings/ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apiKey":"never-return-this-key","baseUrl":"https://api.example.test",
                                 "model":"model-a"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.source").value("SAVED_LOCAL"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("never-return-this-key"));
        assertFalse(response.contains("apiKey\""));

        String loaded = mockMvc.perform(get("/api/settings/ai"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(loaded.contains("never-return-this-key"));
        assertFalse(loaded.contains("apiKey\""));
    }

    @Test
    void rejectsAnOversizedKeyWithoutEchoingItInTheResponse() throws Exception {
        AiSettingsService service = service(tempDir.resolve("settings"), "");
        AiConnectionTester tester = mock(AiConnectionTester.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiSettingsController(service, tester))
                .setControllerAdvice(new GradingExceptionHandler())
                .build();
        String oversizedKey = "private-value-" + "x".repeat(500);

        String response = mockMvc.perform(put("/api/settings/ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apiKey":"%s","baseUrl":"https://api.example.test",
                                 "model":"model-a"}
                                """.formatted(oversizedKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("API Key 长度不合法"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains(oversizedKey));
    }

    private AiSettingsService service(Path directory, String environmentKey) {
        return new AiSettingsService(
                new AiProperties(
                        environmentKey,
                        URI.create("https://api.deepseek.com"),
                        "environment-model",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        1200
                ),
                new AiSettingsProperties(directory.toString())
        );
    }

    private ResponseCreator successResponse() {
        return withSuccess("""
                {"choices":[{"finish_reason":"stop","message":{"content":"{}"}}]}
                """, MediaType.APPLICATION_JSON);
    }

    private void assertOwnerOnly(Path directory, Path... files) throws Exception {
        PosixFileAttributeView posix = Files.getFileAttributeView(directory, PosixFileAttributeView.class);
        if (posix != null) {
            assertEquals(
                    java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE),
                    Files.getPosixFilePermissions(directory));
            for (Path file : files) {
                assertEquals(
                        java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                        Files.getPosixFilePermissions(file));
            }
            return;
        }

        for (Path path : java.util.stream.Stream.concat(
                java.util.stream.Stream.of(directory), java.util.Arrays.stream(files)).toList()) {
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            var owner = Files.getOwner(path);
            assertTrue(acl.getAcl().stream().allMatch(entry -> entry.principal().equals(owner)));
        }
    }
}
