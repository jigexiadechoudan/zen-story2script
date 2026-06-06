package dev.zen.story2script.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 章节解析工具，负责在调用 LLM 之前先做确定性的小说章节切分。
 *
 * <p>MVP 阶段只识别少量可预测的章节标题：中文数字章节（如“第1章”“第一章”）
 * 和英文标题（如“Chapter 1”）。正则里用 Unicode code point 表示中文字符，
 * 避免 Windows 控制台编码导致源码显示或编辑时乱码。</p>
 */
@Component
public class ChapterParseTool {

    private static final int MIN_CHAPTER_COUNT = 3;

    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "^(\\s*(?:(?:\\x{7b2c}\\s*(?:[0-9\\uff10-\\uff19]+|[\\x{96f6}\\x{3007}\\x{4e00}\\x{4e8c}\\x{4e09}\\x{56db}\\x{4e94}\\x{516d}\\x{4e03}\\x{516b}\\x{4e5d}\\x{5341}\\x{767e}\\x{5343}\\x{4e07}\\x{4e24}]+)\\s*\\x{7ae0})|(?:Chapter\\s+[0-9]+\\b))[^\\r\\n]*)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    /**
     * 把原文切成有序章节，并校验至少 3 章。
     *
     * <p>章节数不足属于业务校验失败，返回 valid=false 和明确错误信息；
     * 不直接抛异常，方便调用方展示已识别到的部分章节。</p>
     */
    @Tool(description = "Parse novel text into chapters and validate that at least three chapters are present.")
    public ChapterParseOutput parse(ChapterParseInput input) {
        input = ToolInputs.requireInput(input);
        String sourceText = ToolInputs.requireText(input.sourceText(), "sourceText");

        List<HeadingMatch> headings = findHeadings(sourceText);
        List<ParsedChapter> chapters = splitChapters(sourceText, headings);
        if (chapters.size() < MIN_CHAPTER_COUNT) {
            return new ChapterParseOutput(
                    false,
                    "ChapterParseTool requires at least 3 chapters, but found %d. Supported headings: \u7b2c1\u7ae0, \u7b2c\u4e00\u7ae0, Chapter 1."
                            .formatted(chapters.size()),
                    chapters
            );
        }

        return new ChapterParseOutput(true, "", chapters);
    }

    private List<HeadingMatch> findHeadings(String sourceText) {
        Matcher matcher = CHAPTER_HEADING.matcher(sourceText);
        List<HeadingMatch> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new HeadingMatch(matcher.group(1).trim(), matcher.start(1), matcher.end(1)));
        }
        return headings;
    }

    private List<ParsedChapter> splitChapters(String sourceText, List<HeadingMatch> headings) {
        List<ParsedChapter> chapters = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            HeadingMatch current = headings.get(i);
            // 标题行只作为元数据，章节正文从标题行换行之后开始。
            int contentStart = skipLineBreak(sourceText, current.end());
            int contentEnd = i + 1 < headings.size() ? headings.get(i + 1).start() : sourceText.length();
            String content = sourceText.substring(contentStart, contentEnd).trim();
            chapters.add(new ParsedChapter(i + 1, current.heading(), content));
        }
        return List.copyOf(chapters);
    }

    private int skipLineBreak(String sourceText, int index) {
        if (index < sourceText.length() && sourceText.charAt(index) == '\r') {
            index++;
        }
        if (index < sourceText.length() && sourceText.charAt(index) == '\n') {
            index++;
        }
        return index;
    }

    /**
     * 输入：Agent 或 API 层传入的小说原文。
     */
    public record ChapterParseInput(String sourceText) {
    }

    /**
     * 输出：章节解析结果。
     *
     * <p>valid=false 表示业务校验失败，不代表运行时异常；chapters 里可能仍有
     * 可用于诊断的部分解析结果。</p>
     */
    public record ChapterParseOutput(boolean valid, String errorMessage, List<ParsedChapter> chapters) {
        public ChapterParseOutput {
            chapters = List.copyOf(chapters == null ? List.of() : chapters);
        }
    }

    /**
     * 单个章节。
     *
     * <p>index 是从 1 开始的识别顺序，不一定等于原文标题里写的章节编号。</p>
     */
    public record ParsedChapter(int index, String heading, String content) {
    }

    private record HeadingMatch(String heading, int start, int end) {
    }
}
