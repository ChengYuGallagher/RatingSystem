package com.example.ratingsystem.answerimport;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.ratingsystem.answerimport.AnswerImportDtos.AnswerImportPreview;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.ImportIssue;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.ImportQuestionType;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.ImportStructureRequest;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.ParseStatus;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.ParsedAnswer;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.SectionSpec;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.StudentIdentity;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.UnassignedTextRange;

@Service
public class DocxAnswerImportService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_QUESTION_COUNT = 500;
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(
            "^([A-Za-z0-9-]+)_([^_\\r\\n/\\\\]+)\\.docx$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUESTION_MARKER_PATTERN = Pattern.compile(
            "^\\s*(?:[（(]\\s*(\\d{1,3})\\s*[）)]|(\\d{1,3})\\s*[.．、:：)）])"
                    + "[ \\t　]?(.*)$",
            Pattern.DOTALL);
    private static final Pattern TABLE_NUMBER_PATTERN = Pattern.compile(
            "^\\s*(?:[（(]\\s*)?(\\d{1,3})(?:\\s*[）)]|\\s*[.．、:：)）])?\\s*$");
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile(
            "^(?:[一二三四五六七八九十0-9]+[、.．]?\\s*)?"
                    + "(?:单项选择题|选择题|填空题|判断题|简答题|编程题|程序设计题)"
                    + "(?:\\s*[（(][^）)]*[）)])?\\s*$");

    public AnswerImportPreview preview(MultipartFile file, ImportStructureRequest structure) {
        if (file == null || file.isEmpty()) {
            throw new AnswerImportException("必须上传非空 DOCX 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AnswerImportException("DOCX 文件不能超过 10 MB");
        }
        try {
            return preview(file.getOriginalFilename(), file.getBytes(), structure);
        } catch (IOException exception) {
            throw new AnswerImportException("读取 DOCX 文件失败", exception);
        }
    }

    public AnswerImportPreview preview(String originalFilename, byte[] content,
                                       ImportStructureRequest structure) {
        StudentIdentity identity = identify(originalFilename);
        if (content == null || content.length == 0) {
            throw new AnswerImportException("必须上传非空 DOCX 文件");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new AnswerImportException("DOCX 文件不能超过 10 MB");
        }
        List<ExpectedQuestion> expectedQuestions = buildExpectedQuestions(structure);
        List<TextBlock> blocks = readBlocks(content);
        List<NumberedBlock> candidates = findCandidates(blocks);
        Alignment alignment = align(expectedQuestions, candidates);
        boolean exactSequence = hasExactNumberSequence(expectedQuestions, candidates);
        TrailingRecovery trailingRecovery = recoverTrailingUnnumberedQuestions(
                blocks, expectedQuestions, candidates);
        boolean sequenceAmbiguous = !exactSequence && !trailingRecovery.successful();

        Map<Integer, AnswerSource> sourceByExpected = new LinkedHashMap<>();
        alignment.candidateByExpected().forEach((expectedIndex, candidateIndex) -> {
            Integer forcedEnd = trailingRecovery.attempted()
                    && expectedIndex == trailingRecovery.numberedPrefixEndExpectedIndex()
                    ? trailingRecovery.numberedEndExclusive()
                    : null;
            sourceByExpected.put(expectedIndex, extractNumberedAnswer(
                    blocks, candidates, candidateIndex, forcedEnd));
        });
        sourceByExpected.putAll(trailingRecovery.recoveredSources());

        List<ImportIssue> globalIssues = new ArrayList<>();
        if (sequenceAmbiguous) {
            globalIssues.add(new ImportIssue(
                    "QUESTION_SEQUENCE_AMBIGUOUS",
                    "文档中的编号序列与配置不完全一致，已保守对齐，必须由教师确认缺题、额外编号或题型边界",
                    null,
                    null
            ));
        }
        if (trailingRecovery.successful()) {
            globalIssues.add(new ImportIssue(
                    "UNNUMBERED_TRAILING_QUESTIONS_RECOVERED",
                    "编号题结束后存在与剩余题目数量一致的空段落分组，已按配置顺序恢复无编号题目",
                    null,
                    null
            ));
        } else if (trailingRecovery.attempted()) {
            globalIssues.add(new ImportIssue(
                    "TRAILING_BOUNDARY_AMBIGUOUS",
                    "编号题后的文本无法按空段落可靠切分为剩余题目，已保留为未分配原文",
                    null,
                    null
            ));
        }

        int unmappedCount = 0;
        int recognizedCount = 0;
        List<ParsedAnswer> answers = new ArrayList<>();
        for (int expectedIndex = 0; expectedIndex < expectedQuestions.size(); expectedIndex++) {
            ExpectedQuestion expected = expectedQuestions.get(expectedIndex);
            AnswerSource source = sourceByExpected.get(expectedIndex);
            List<ImportIssue> itemIssues = new ArrayList<>();
            String rawAnswer = null;
            ParseStatus itemStatus;

            if (source == null) {
                itemStatus = ParseStatus.FAILED;
                itemIssues.add(issue("QUESTION_NOT_FOUND", "未找到该题对应的编号和答案",
                        expected.type(), expected.questionNo()));
            } else {
                recognizedCount++;
                rawAnswer = source.rawText();
                itemStatus = ParseStatus.SUCCESS;
                if (source.inferred()) {
                    itemStatus = ParseStatus.NEEDS_REVIEW;
                    itemIssues.add(issue("QUESTION_NUMBER_INFERRED_FROM_POSITION",
                            "该题没有显式编号，依据空段落分组和剩余题型顺序恢复",
                            expected.type(), expected.questionNo()));
                } else if (sequenceAmbiguous) {
                    itemStatus = ParseStatus.NEEDS_REVIEW;
                    itemIssues.add(issue("QUESTION_BOUNDARY_NEEDS_REVIEW",
                            "编号序列存在差异，本题边界需要教师确认", expected.type(), expected.questionNo()));
                }
                if (rawAnswer.isEmpty()) {
                    itemStatus = ParseStatus.NEEDS_REVIEW;
                    itemIssues.add(issue("EMPTY_ANSWER", "识别到题号，但题号后没有答案内容",
                            expected.type(), expected.questionNo()));
                }
                if (expected.questionId() == null) {
                    unmappedCount++;
                    itemStatus = ParseStatus.NEEDS_REVIEW;
                    itemIssues.add(issue("QUESTION_ID_UNMAPPED", "未提供可靠的数据库题目 ID，未执行猜测映射",
                            expected.type(), expected.questionNo()));
                }
            }
            answers.add(new ParsedAnswer(expected.questionId(), expected.type(), expected.questionNo(),
                    rawAnswer, source == null ? null : source.startBlock(),
                    source == null ? null : source.endBlock(), itemStatus, List.copyOf(itemIssues)));
        }

        List<UnassignedTextRange> unassignedTextRanges = trailingRecovery.unassignedTextRanges();
        if (sequenceAmbiguous && unassignedTextRanges.isEmpty()) {
            unassignedTextRanges = findUnassignedText(blocks, sourceByExpected);
        }
        if (!unassignedTextRanges.isEmpty()) {
            globalIssues.add(new ImportIssue(
                    "UNASSIGNED_TEXT_PRESENT",
                    "存在无法可靠归属到题目的原始文本，请结合 sourceStartBlock 和 sourceEndBlock 人工确认",
                    null,
                    null
            ));
        }

        if (unmappedCount > 0) {
            globalIssues.add(new ImportIssue(
                    "QUESTION_IDS_UNMAPPED",
                    unmappedCount + " 道题未提供可靠的数据库题目 ID",
                    null,
                    null
            ));
        }
        if (recognizedCount == 0) {
            globalIssues.add(new ImportIssue(
                    "NO_QUESTION_RECOGNIZED",
                    "没有识别到符合配置的题号",
                    null,
                    null
            ));
        }

        ParseStatus overallStatus = determineOverallStatus(answers, globalIssues, recognizedCount);
        return new AnswerImportPreview(identity.studentNo(), identity.studentName(), identity.filename(),
                expectedQuestions.size(), recognizedCount, blocks.size(), overallStatus, List.copyOf(answers),
                List.copyOf(unassignedTextRanges), List.copyOf(globalIssues));
    }

    public StudentIdentity identify(String originalFilename) {
        String filename = cleanFilename(originalFilename);
        Matcher matcher = FILE_NAME_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new AnswerImportException("文件名必须符合 学号_姓名.docx，例如 244071101_张三.docx");
        }
        return new StudentIdentity(matcher.group(1), matcher.group(2), filename);
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String normalized = originalFilename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private List<ExpectedQuestion> buildExpectedQuestions(ImportStructureRequest structure) {
        if (structure == null || structure.sections() == null || structure.sections().isEmpty()) {
            throw new AnswerImportException("必须提供至少一个题型分区");
        }
        List<ExpectedQuestion> expected = new ArrayList<>();
        Set<Long> questionIds = new HashSet<>();
        for (SectionSpec section : structure.sections()) {
            if (section == null || section.questionType() == null || section.questionCount() < 1
                    || section.questionCount() > 200) {
                throw new AnswerImportException("题型分区及题目数量不合法");
            }
            List<Long> ids = section.questionIds();
            if (ids != null && !ids.isEmpty() && ids.size() != section.questionCount()) {
                throw new AnswerImportException(section.questionType() + " 的 questionIds 数量必须与 questionCount 一致");
            }
            for (int questionNo = 1; questionNo <= section.questionCount(); questionNo++) {
                Long questionId = ids == null || ids.isEmpty() ? null : ids.get(questionNo - 1);
                if (questionId != null && (questionId <= 0 || !questionIds.add(questionId))) {
                    throw new AnswerImportException("questionIds 必须为不重复的正整数");
                }
                expected.add(new ExpectedQuestion(section.questionType(), questionNo, questionId));
                if (expected.size() > MAX_QUESTION_COUNT) {
                    throw new AnswerImportException("单份答卷最多支持 500 道题");
                }
            }
        }
        return expected;
    }

    private List<TextBlock> readBlocks(byte[] content) {
        try (InputStream inputStream = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            List<TextBlock> blocks = new ArrayList<>();
            Map<NumberingKey, Integer> numberingCounters = new LinkedHashMap<>();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    blocks.add(new TextBlock(paragraph.getText(),
                            automaticNumber(paragraph, numberingCounters)));
                } else if (element instanceof XWPFTable table) {
                    addTableBlocks(table, blocks);
                }
            }
            return blocks;
        } catch (Exception exception) {
            throw new AnswerImportException("文件不是可读取的 DOCX，或文件已经损坏", exception);
        }
    }

    private Integer automaticNumber(XWPFParagraph paragraph, Map<NumberingKey, Integer> counters) {
        BigInteger numberingId = paragraph.getNumID();
        BigInteger level = paragraph.getNumIlvl();
        String numberFormat = paragraph.getNumFmt();
        if (numberingId == null || (level != null && level.signum() != 0)
                || !("decimal".equals(numberFormat) || "decimalZero".equals(numberFormat))) {
            return null;
        }
        NumberingKey key = new NumberingKey(numberingId, level == null ? BigInteger.ZERO : level);
        Integer previous = counters.get(key);
        int number = previous == null
                ? (paragraph.getNumStartOverride() == null ? 1 : paragraph.getNumStartOverride().intValue())
                : previous + 1;
        counters.put(key, number);
        return number;
    }

    private void addTableBlocks(XWPFTable table, List<TextBlock> blocks) {
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            List<String> cellTexts = cells.stream().map(this::cellText).toList();
            Matcher numberCell = cellTexts.isEmpty()
                    ? TABLE_NUMBER_PATTERN.matcher("")
                    : TABLE_NUMBER_PATTERN.matcher(cellTexts.get(0));
            if (cellTexts.size() >= 2 && numberCell.matches()) {
                String answer = String.join("\n", cellTexts.subList(1, cellTexts.size()));
                blocks.add(new TextBlock(numberCell.group(1) + ". " + answer, null));
            } else {
                cellTexts.forEach(text -> blocks.add(new TextBlock(text, null)));
            }
        }
    }

    private String cellText(XWPFTableCell cell) {
        List<String> parts = new ArrayList<>();
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            parts.add(paragraph.getText());
        }
        for (XWPFTable nestedTable : cell.getTables()) {
            for (XWPFTableRow row : nestedTable.getRows()) {
                for (XWPFTableCell nestedCell : row.getTableCells()) {
                    parts.add(cellText(nestedCell));
                }
            }
        }
        return String.join("\n", parts);
    }

    private List<NumberedBlock> findCandidates(List<TextBlock> blocks) {
        List<NumberedBlock> candidates = new ArrayList<>();
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            String text = blocks.get(blockIndex).text();
            Matcher matcher = QUESTION_MARKER_PATTERN.matcher(text);
            if (matcher.matches()) {
                String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                candidates.add(new NumberedBlock(blockIndex, Integer.parseInt(number), matcher.group(3)));
            } else if (blocks.get(blockIndex).automaticNumber() != null) {
                candidates.add(new NumberedBlock(blockIndex, blocks.get(blockIndex).automaticNumber(), text));
            }
        }
        return candidates;
    }

    private Alignment align(List<ExpectedQuestion> expected, List<NumberedBlock> candidates) {
        int[][] matches = new int[expected.size() + 1][candidates.size() + 1];
        for (int expectedIndex = expected.size() - 1; expectedIndex >= 0; expectedIndex--) {
            for (int candidateIndex = candidates.size() - 1; candidateIndex >= 0; candidateIndex--) {
                int best = Math.max(matches[expectedIndex + 1][candidateIndex],
                        matches[expectedIndex][candidateIndex + 1]);
                if (expected.get(expectedIndex).questionNo() == candidates.get(candidateIndex).questionNo()) {
                    best = Math.max(best, 1 + matches[expectedIndex + 1][candidateIndex + 1]);
                }
                matches[expectedIndex][candidateIndex] = best;
            }
        }

        Map<Integer, Integer> candidateByExpected = new LinkedHashMap<>();
        int expectedIndex = 0;
        int candidateIndex = 0;
        while (expectedIndex < expected.size() && candidateIndex < candidates.size()) {
            if (expected.get(expectedIndex).questionNo() == candidates.get(candidateIndex).questionNo()
                    && matches[expectedIndex][candidateIndex]
                    == 1 + matches[expectedIndex + 1][candidateIndex + 1]) {
                candidateByExpected.put(expectedIndex, candidateIndex);
                expectedIndex++;
                candidateIndex++;
            } else if (matches[expectedIndex][candidateIndex + 1]
                    >= matches[expectedIndex + 1][candidateIndex]) {
                candidateIndex++;
            } else {
                expectedIndex++;
            }
        }
        return new Alignment(candidateByExpected);
    }

    private boolean hasExactNumberSequence(List<ExpectedQuestion> expected, List<NumberedBlock> candidates) {
        if (expected.size() != candidates.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (expected.get(index).questionNo() != candidates.get(index).questionNo()) {
                return false;
            }
        }
        return true;
    }

    private TrailingRecovery recoverTrailingUnnumberedQuestions(List<TextBlock> blocks,
                                                                List<ExpectedQuestion> expected,
                                                                List<NumberedBlock> candidates) {
        if (candidates.isEmpty() || candidates.size() >= expected.size()) {
            return TrailingRecovery.notAttempted();
        }
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).questionNo() != expected.get(index).questionNo()) {
                return TrailingRecovery.notAttempted();
            }
        }

        int prefixEndExpectedIndex = candidates.size() - 1;
        int numberedEndExclusive = candidates.get(candidates.size() - 1).blockIndex() + 1;
        int missingCount = expected.size() - candidates.size();
        int cursor = numberedEndExclusive;
        if (cursor >= blocks.size() || !blocks.get(cursor).text().isEmpty()) {
            return TrailingRecovery.failed(prefixEndExpectedIndex, numberedEndExclusive,
                    collectTextRanges(blocks, cursor, blocks.size(), "缺少分隔编号题和后续无编号题的空段落"));
        }
        while (cursor < blocks.size() && blocks.get(cursor).text().isEmpty()) {
            cursor++;
        }

        List<BlockGroup> groups = splitNonEmptyGroups(blocks, cursor);
        if (groups.size() != missingCount) {
            return TrailingRecovery.failed(prefixEndExpectedIndex, numberedEndExclusive,
                    collectTextRanges(blocks, cursor, blocks.size(),
                            "空段落分组数量与剩余题目数量不一致"));
        }

        Map<Integer, AnswerSource> recoveredSources = new LinkedHashMap<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            BlockGroup group = groups.get(groupIndex);
            recoveredSources.put(candidates.size() + groupIndex,
                    new AnswerSource(group.startBlock(), group.endBlock(), group.rawText(), true));
        }
        return TrailingRecovery.success(prefixEndExpectedIndex, numberedEndExclusive, recoveredSources);
    }

    private List<BlockGroup> splitNonEmptyGroups(List<TextBlock> blocks, int startBlock) {
        List<BlockGroup> groups = new ArrayList<>();
        int cursor = startBlock;
        while (cursor < blocks.size()) {
            while (cursor < blocks.size() && blocks.get(cursor).text().isEmpty()) {
                cursor++;
            }
            if (cursor >= blocks.size()) {
                break;
            }
            int groupStart = cursor;
            List<String> parts = new ArrayList<>();
            while (cursor < blocks.size() && !blocks.get(cursor).text().isEmpty()) {
                if (!isSectionHeading(blocks.get(cursor).text())) {
                    parts.add(blocks.get(cursor).text());
                }
                cursor++;
            }
            int groupEnd = cursor - 1;
            groups.add(new BlockGroup(groupStart, groupEnd, String.join("\n", parts)));
        }
        return groups;
    }

    private AnswerSource extractNumberedAnswer(List<TextBlock> blocks, List<NumberedBlock> candidates,
                                               int candidateIndex, Integer forcedEndExclusive) {
        NumberedBlock current = candidates.get(candidateIndex);
        int endExclusive = forcedEndExclusive != null
                ? forcedEndExclusive
                : candidateIndex + 1 < candidates.size()
                        ? candidates.get(candidateIndex + 1).blockIndex()
                        : blocks.size();
        List<String> parts = new ArrayList<>();
        if (!current.answerOnMarker().isEmpty()) {
            parts.add(current.answerOnMarker());
        }
        int finalContentBlock = current.blockIndex();
        for (int blockIndex = current.blockIndex() + 1; blockIndex < endExclusive; blockIndex++) {
            String text = blocks.get(blockIndex).text();
            if (!isSectionHeading(text)) {
                parts.add(text);
                if (!text.isEmpty()) {
                    finalContentBlock = blockIndex;
                }
            }
        }
        while (!parts.isEmpty() && parts.get(parts.size() - 1).isEmpty()) {
            parts.remove(parts.size() - 1);
        }
        return new AnswerSource(current.blockIndex(), finalContentBlock, String.join("\n", parts), false);
    }

    private List<UnassignedTextRange> findUnassignedText(List<TextBlock> blocks,
                                                         Map<Integer, AnswerSource> sources) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        boolean[] assigned = new boolean[blocks.size()];
        int firstAssignedBlock = blocks.size();
        for (AnswerSource source : sources.values()) {
            firstAssignedBlock = Math.min(firstAssignedBlock, source.startBlock());
            for (int index = source.startBlock(); index <= source.endBlock() && index < assigned.length; index++) {
                assigned[index] = true;
            }
        }
        int scanStart = firstAssignedBlock == blocks.size() ? 0 : firstAssignedBlock;
        List<UnassignedTextRange> ranges = new ArrayList<>();
        int cursor = scanStart;
        while (cursor < blocks.size()) {
            while (cursor < blocks.size() && (assigned[cursor] || blocks.get(cursor).text().isEmpty())) {
                cursor++;
            }
            if (cursor >= blocks.size()) {
                break;
            }
            int start = cursor;
            List<String> parts = new ArrayList<>();
            while (cursor < blocks.size() && !assigned[cursor]) {
                parts.add(blocks.get(cursor).text());
                cursor++;
            }
            while (!parts.isEmpty() && parts.get(parts.size() - 1).isEmpty()) {
                parts.remove(parts.size() - 1);
            }
            if (!parts.isEmpty()) {
                ranges.add(new UnassignedTextRange(start, cursor - 1, String.join("\n", parts),
                        "编号序列存在歧义，无法可靠分配"));
            }
        }
        return List.copyOf(ranges);
    }

    private List<UnassignedTextRange> collectTextRanges(List<TextBlock> blocks, int startBlock, int endExclusive,
                                                        String reason) {
        if (startBlock >= endExclusive || startBlock >= blocks.size()) {
            return List.of();
        }
        List<UnassignedTextRange> ranges = new ArrayList<>();
        int cursor = Math.max(startBlock, 0);
        int limit = Math.min(endExclusive, blocks.size());
        while (cursor < limit) {
            while (cursor < limit && blocks.get(cursor).text().isEmpty()) {
                cursor++;
            }
            if (cursor >= limit) {
                break;
            }
            int rangeStart = cursor;
            List<String> parts = new ArrayList<>();
            while (cursor < limit && !blocks.get(cursor).text().isEmpty()) {
                parts.add(blocks.get(cursor).text());
                cursor++;
            }
            ranges.add(new UnassignedTextRange(rangeStart, cursor - 1, String.join("\n", parts), reason));
        }
        return List.copyOf(ranges);
    }

    private boolean isSectionHeading(String text) {
        return SECTION_HEADING_PATTERN.matcher(text.strip()).matches();
    }

    private ParseStatus determineOverallStatus(List<ParsedAnswer> answers, List<ImportIssue> issues,
                                               int recognizedCount) {
        if (recognizedCount == 0) {
            return ParseStatus.FAILED;
        }
        boolean allSuccessful = issues.isEmpty()
                && answers.stream().allMatch(answer -> answer.parseStatus() == ParseStatus.SUCCESS);
        return allSuccessful ? ParseStatus.SUCCESS : ParseStatus.NEEDS_REVIEW;
    }

    private ImportIssue issue(String code, String message, ImportQuestionType type, int questionNo) {
        return new ImportIssue(code, message, type, questionNo);
    }

    private record TextBlock(String text, Integer automaticNumber) {
        private TextBlock {
            text = text == null ? "" : text;
        }
    }

    private record NumberedBlock(int blockIndex, int questionNo, String answerOnMarker) {
    }

    private record ExpectedQuestion(ImportQuestionType type, int questionNo, Long questionId) {
    }

    private record Alignment(Map<Integer, Integer> candidateByExpected) {
    }

    private record NumberingKey(BigInteger numberingId, BigInteger level) {
    }

    private record AnswerSource(int startBlock, int endBlock, String rawText, boolean inferred) {
    }

    private record BlockGroup(int startBlock, int endBlock, String rawText) {
    }

    private record TrailingRecovery(boolean attempted, boolean successful,
                                    int numberedPrefixEndExpectedIndex, Integer numberedEndExclusive,
                                    Map<Integer, AnswerSource> recoveredSources,
                                    List<UnassignedTextRange> unassignedTextRanges) {

        private static TrailingRecovery notAttempted() {
            return new TrailingRecovery(false, false, -1, null, Map.of(), List.of());
        }

        private static TrailingRecovery success(int prefixEnd, int numberedEnd,
                                                Map<Integer, AnswerSource> sources) {
            return new TrailingRecovery(true, true, prefixEnd, numberedEnd,
                    Map.copyOf(sources), List.of());
        }

        private static TrailingRecovery failed(int prefixEnd, int numberedEnd,
                                               List<UnassignedTextRange> ranges) {
            return new TrailingRecovery(true, false, prefixEnd, numberedEnd, Map.of(), List.copyOf(ranges));
        }
    }
}
