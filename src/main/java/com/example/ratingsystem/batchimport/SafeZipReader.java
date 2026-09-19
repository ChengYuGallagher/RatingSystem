package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.answerimport.AnswerImportException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Component
class SafeZipReader {

    static final long MAX_ARCHIVE_SIZE = 50L * 1024 * 1024;
    static final int MAX_ENTRY_COUNT = 1_000;
    static final int MAX_DOCX_COUNT = 200;
    static final int MAX_PATH_LENGTH = 700;
    static final long MAX_SINGLE_FILE_SIZE = 10L * 1024 * 1024;
    static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 200L * 1024 * 1024;
    private static final long MAX_COMPRESSION_RATIO = 200;

    ArchiveContent read(MultipartFile archive) {
        validateArchive(archive);
        List<ArchiveDocx> documents = new ArrayList<>();
        List<ArchiveIssue> issues = new ArrayList<>();
        Set<String> directories = new LinkedHashSet<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        long totalBytes = 0;
        int entryCount = 0;

        try (InputStream input = archive.getInputStream(); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new AnswerImportException("ZIP 条目数量超过上限 " + MAX_ENTRY_COUNT);
                }
                String path = validateAndNormalizePath(entry.getName());
                if (!seenPaths.add(path)) {
                    issues.add(new ArchiveIssue("DUPLICATE_ZIP_ENTRY", "ZIP 中存在重复路径: " + path));
                    drain(zip, MAX_SINGLE_FILE_SIZE);
                    continue;
                }
                registerParentDirectories(path, directories);
                if (entry.isDirectory()) {
                    directories.add(trimTrailingSlash(path));
                    continue;
                }
                if (entry.getSize() > MAX_SINGLE_FILE_SIZE) {
                    throw new AnswerImportException("ZIP 内单个文件超过 10 MB: " + path);
                }

                byte[] bytes = readEntry(zip, path);
                totalBytes += bytes.length;
                if (totalBytes > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                    throw new AnswerImportException("ZIP 解压后总大小超过 200 MB");
                }
                validateCompressionRatio(entry, bytes.length, path);

                if (path.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                    if (documents.size() >= MAX_DOCX_COUNT) {
                        throw new AnswerImportException("ZIP 中 DOCX 数量超过上限 " + MAX_DOCX_COUNT);
                    }
                    documents.add(new ArchiveDocx(path, bytes));
                } else {
                    issues.add(new ArchiveIssue("UNSUPPORTED_FILE",
                            "已忽略不支持的文件，仅支持 DOCX: " + path));
                }
            }
        } catch (AnswerImportException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw new AnswerImportException("上传文件不是有效的 ZIP，或 ZIP 已损坏", exception);
        } catch (IOException exception) {
            throw new AnswerImportException("读取 ZIP 失败", exception);
        }
        return new ArchiveContent(List.copyOf(documents), Set.copyOf(directories), List.copyOf(issues));
    }

    private void validateArchive(MultipartFile archive) {
        if (archive == null || archive.isEmpty()) {
            throw new AnswerImportException("必须上传非空 ZIP 文件");
        }
        if (archive.getSize() > MAX_ARCHIVE_SIZE) {
            throw new AnswerImportException("ZIP 文件不能超过 50 MB");
        }
        String name = archive.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new AnswerImportException("只支持 ZIP 批量上传");
        }
    }

    private String validateAndNormalizePath(String original) {
        if (original == null || original.isBlank() || original.indexOf('\0') >= 0) {
            throw new AnswerImportException("ZIP 包含非法空路径");
        }
        String path = original.replace('\\', '/');
        if (path.length() > MAX_PATH_LENGTH || path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw new AnswerImportException("ZIP 包含非法绝对路径: " + original);
        }
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                throw new AnswerImportException("ZIP 包含路径穿越条目: " + original);
            }
        }
        return path;
    }

    private byte[] readEntry(ZipInputStream zip, String path) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int read;
        long size = 0;
        while ((read = zip.read(buffer)) != -1) {
            size += read;
            if (size > MAX_SINGLE_FILE_SIZE) {
                throw new AnswerImportException("ZIP 内单个文件超过 10 MB: " + path);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void drain(ZipInputStream zip, long limit) throws IOException {
        byte[] buffer = new byte[8_192];
        long size = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            size += read;
            if (size > limit) {
                throw new AnswerImportException("ZIP 重复条目的大小超过限制");
            }
        }
    }

    private void validateCompressionRatio(ZipEntry entry, long uncompressedSize, String path) {
        long compressedSize = entry.getCompressedSize();
        if (compressedSize > 0 && uncompressedSize > compressedSize * MAX_COMPRESSION_RATIO) {
            throw new AnswerImportException("ZIP 条目压缩比异常，拒绝解压: " + path);
        }
    }

    private void registerParentDirectories(String path, Set<String> directories) {
        int slash = path.lastIndexOf('/');
        while (slash > 0) {
            directories.add(path.substring(0, slash));
            slash = path.lastIndexOf('/', slash - 1);
        }
    }

    private String trimTrailingSlash(String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    record ArchiveContent(List<ArchiveDocx> documents, Set<String> directories, List<ArchiveIssue> issues) {
    }

    record ArchiveDocx(String path, byte[] bytes) {
    }

    record ArchiveIssue(String code, String message) {
    }
}
