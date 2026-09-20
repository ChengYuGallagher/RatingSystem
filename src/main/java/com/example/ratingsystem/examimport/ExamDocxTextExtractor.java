package com.example.ratingsystem.examimport;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
class ExamDocxTextExtractor {

    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 200_000;

    ExtractedDocument extract(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new ExamPaperImportException(fieldName + "不能为空");
        }
        String filename = cleanFilename(file.getOriginalFilename());
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".docx")) {
            throw new ExamPaperImportException(fieldName + "必须是 DOCX 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ExamPaperImportException(fieldName + "不能超过 10 MB");
        }
        try {
            return extract(filename, file.getBytes());
        } catch (IOException exception) {
            throw new ExamPaperImportException("读取" + fieldName + "失败", exception);
        }
    }

    private ExtractedDocument extract(String filename, byte[] bytes) {
        List<String> blocks = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    addBlock(blocks, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        addBlock(blocks, row.getTableCells().stream()
                                .map(XWPFTableCell::getText)
                                .map(String::strip)
                                .filter(value -> !value.isEmpty())
                                .collect(java.util.stream.Collectors.joining("\t")));
                    }
                }
            }
        } catch (Exception exception) {
            throw new ExamPaperImportException("DOCX 文件损坏、加密或格式不受支持", exception);
        }
        if (blocks.isEmpty()) {
            throw new ExamPaperImportException("DOCX 中没有可读取的文字；当前不支持扫描图片或 OCR");
        }
        String text = String.join("\n", blocks);
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ExamPaperImportException("DOCX 文字内容过长，最多支持 200000 个字符");
        }
        return new ExtractedDocument(filename, List.copyOf(blocks), text);
    }

    private void addBlock(List<String> blocks, String value) {
        String normalized = value == null ? "" : value.replace('\u00a0', ' ').strip();
        if (!normalized.isEmpty()) {
            blocks.add(normalized);
        }
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        String normalized = originalFilename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    record ExtractedDocument(String filename, List<String> blocks, String text) {
    }
}
