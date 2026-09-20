package com.example.ratingsystem.grading.ai;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

final class EncryptedLocalAiSettingsStore {

    private static final int KEY_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path directory;
    private final Path settingsFile;
    private final Path keyFile;

    EncryptedLocalAiSettingsStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        this.settingsFile = this.directory.resolve("ai-settings.properties");
        this.keyFile = this.directory.resolve("ai-settings.key");
    }

    Optional<SavedAiSettings> load() {
        if (!Files.exists(settingsFile)) {
            return Optional.empty();
        }
        try {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(settingsFile)) {
                properties.load(input);
            }
            String encryptedKey = properties.getProperty("apiKeyCiphertext", "");
            String apiKey = encryptedKey.isBlank() ? "" : decrypt(encryptedKey, readMasterKey());
            return Optional.of(new SavedAiSettings(
                    apiKey,
                    properties.getProperty("baseUrl", ""),
                    properties.getProperty("model", ""),
                    Instant.parse(properties.getProperty("updatedAt"))
            ));
        } catch (Exception exception) {
            throw new AiSettingsException("无法读取本机 AI 配置，请重新输入并保存配置", exception);
        }
    }

    void save(SavedAiSettings settings) {
        try {
            Files.createDirectories(directory);
            restrictAccess(directory, true);
            String encryptedKey = settings.apiKey().isBlank() ? "" : encrypt(settings.apiKey(), loadOrCreateMasterKey());
            Properties properties = new Properties();
            properties.setProperty("version", "1");
            properties.setProperty("apiKeyCiphertext", encryptedKey);
            properties.setProperty("baseUrl", settings.baseUrl());
            properties.setProperty("model", settings.model());
            properties.setProperty("updatedAt", settings.updatedAt().toString());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            properties.store(output, "RatingSystem local AI settings");
            atomicWrite(settingsFile, output.toByteArray());
        } catch (IOException | GeneralSecurityException exception) {
            throw new AiSettingsException("无法保存本机 AI 配置，请检查当前用户的文件访问权限", exception);
        }
    }

    private byte[] loadOrCreateMasterKey() throws IOException {
        if (Files.exists(keyFile)) {
            return readMasterKey();
        }
        byte[] key = new byte[KEY_LENGTH];
        RANDOM.nextBytes(key);
        atomicWrite(keyFile, key);
        return key;
    }

    private byte[] readMasterKey() throws IOException {
        byte[] key = Files.readAllBytes(keyFile);
        if (key.length != KEY_LENGTH) {
            throw new IOException("AI settings key has an invalid length");
        }
        return key;
    }

    private String encrypt(String plaintext, byte[] key) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(result);
    }

    private String decrypt(String encoded, byte[] key) throws GeneralSecurityException {
        byte[] value = Base64.getDecoder().decode(encoded);
        if (value.length <= IV_LENGTH) {
            throw new GeneralSecurityException("Invalid encrypted AI key");
        }
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[value.length - IV_LENGTH];
        System.arraycopy(value, 0, iv, 0, iv.length);
        System.arraycopy(value, iv.length, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private void atomicWrite(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, content);
            restrictAccess(temporary, false);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictAccess(target, false);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void restrictAccess(Path path, boolean directoryPath) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> permissions = directoryPath
                    ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            posix.setPermissions(permissions);
            return;
        }

        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) {
            throw new IOException("当前文件系统不支持受限访问权限");
        }
        UserPrincipal owner = Files.getOwner(path);
        AclEntry.Builder entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directoryPath) {
            entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
        }
        acl.setAcl(List.of(entry.build()));
    }

    record SavedAiSettings(String apiKey, String baseUrl, String model, Instant updatedAt) {
    }
}
