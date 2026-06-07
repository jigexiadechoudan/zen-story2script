package dev.zen.story2script.config;

import dev.zen.story2script.tools.ToolLlmClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev-only fallback wiring for tool beans that depend on an LLM client.
 *
 * <p>The default {@code dev} profile starts without an OpenAI API key or chat model. This configuration lets the
 * conversion flow return a schema-valid demonstration YAML while making it explicit that no real model was called.</p>
 */
@Configuration(proxyBeanMethods = false)
public class DevFallbackToolLlmClientConfiguration {

    @Bean
    @Profile("dev")
    @ConditionalOnMissingBean(ChatModel.class)
    ToolLlmClient fallbackToolLlmClient() {
        return new DevFallbackToolLlmClient();
    }

    public static final class DevFallbackToolLlmClient implements ToolLlmClient {

        private static final Pattern TITLE = Pattern.compile("(?m)^Title:\\s*(.+)$");
        private static final Pattern TARGET_FORMAT = Pattern.compile("(?m)^Target format:\\s*(.+)$");
        private static final Pattern TARGET_DURATION = Pattern.compile("(?m)^Target duration:\\s*(.+)$");
        private static final Pattern STYLE_HINT = Pattern.compile("(?m)^Style hint:\\s*(.+)$");
        private static final Pattern LANGUAGE = Pattern.compile("(?m)^Language:\\s*(.+)$");
        private static final Pattern CHAPTER = Pattern.compile("(?m)^\\[(\\d+)]\\s+(.+)$");

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            if (systemPrompt.contains("story analysis tool")) {
                return storyAnalysis(userPrompt);
            }
            if (systemPrompt.contains("scene planning tool")) {
                return scenePlan(userPrompt);
            }
            if (systemPrompt.contains("screenplay YAML writing tool")
                    || systemPrompt.contains("fast novel-to-screenplay YAML agent")) {
                return screenplayYaml(userPrompt);
            }
            if (systemPrompt.contains("YAML repair tool")) {
                return extractYamlToRepair(userPrompt);
            }
            return "";
        }

        @Override
        public boolean devFallback() {
            return true;
        }

        private String storyAnalysis(String prompt) {
            String title = value(prompt, TITLE, "Untitled Work");
            String styleHint = value(prompt, STYLE_HINT, "standard");
            List<Chapter> chapters = chapters(prompt);
            StringBuilder events = new StringBuilder();
            for (Chapter chapter : chapters) {
                if (!events.isEmpty()) {
                    events.append(',');
                }
                events.append("""
                        {"sourceChapter":"%s","summary":"%s","consequence":"Moves the demo adaptation toward the next scene."}"""
                        .formatted(json(chapter.heading()), json(summary(chapter.content()))));
            }
            return """
                    {"characters":[{"name":"%s Protagonist","role":"protagonist","identity":"adapted lead","goal":"Carry the central conflict into a %s adaptation.","relationshipHints":["Connects the parsed chapters into a clear demonstration arc."]},{"name":"Supporting Witness","role":"supporting","identity":"chapter witness","goal":"Reveal pressure from the source material.","relationshipHints":["Contrasts with the protagonist."]}],"events":[%s],"conflicts":[{"parties":["%s Protagonist","Supporting Witness"],"conflict":"The protagonist must act on incomplete information from the parsed chapters.","stakes":"The adaptation needs a playable dramatic spine for demo review."}]}"""
                    .formatted(json(title), json(styleHint), events, json(title));
        }

        private String scenePlan(String prompt) {
            String title = value(prompt, TITLE, "Untitled Work");
            String targetFormat = value(prompt, TARGET_FORMAT, "screenplay");
            int chapterCount = countOccurrences(prompt, "\"sourceChapter\"");
            int sceneCount = Math.max(1, chapterCount);
            StringBuilder scenes = new StringBuilder();
            for (int i = 1; i <= sceneCount; i++) {
                if (!scenes.isEmpty()) {
                    scenes.append(',');
                }
                scenes.append("""
                        {"sceneId":"S%03d","sceneType":"INT/EXT","location":"Demo location %d","timeOfDay":"DAY","characters":["%s Protagonist","Supporting Witness"],"summary":"Demonstration scene %d shaped for %s.","dramaticPurpose":"Show the fallback can produce a structured scene from parsed chapters.","sourceChapters":["Chapter %d"]}"""
                        .formatted(i, i, json(title), i, json(targetFormat), i));
            }
            return """
                    {"scenes":[%s],"adaptationNotes":["Dev fallback generated this plan without calling a real large language model."]}"""
                    .formatted(scenes);
        }

        private String screenplayYaml(String prompt) {
            String title = value(prompt, TITLE, "Untitled Work");
            String language = value(prompt, LANGUAGE, "zh-CN");
            String targetFormat = value(prompt, TARGET_FORMAT, "screenplay");
            String targetDuration = value(prompt, TARGET_DURATION, "demo runtime");
            String styleHint = value(prompt, STYLE_HINT, "standard");
            boolean english = language.toLowerCase().startsWith("en");
            int sceneCount = Math.max(1, countOccurrences(prompt, "\"sceneId\""));
            if (sceneCount == 1) {
                sceneCount = Math.max(1, chapters(prompt).size());
            }
            String chapterRange = sceneCount == 1 ? "Chapter 1" : "Chapter 1-%d".formatted(sceneCount);

            StringBuilder plotOutline = new StringBuilder();
            StringBuilder scenes = new StringBuilder();
            for (int i = 1; i <= sceneCount; i++) {
                plotOutline.append("""
                    - source_chapter: "Chapter %d"
                      key_events:
                        - "Dev fallback extracts a playable event from chapter %d."
                      adaptation_choice: "Compress the chapter into a clear %s beat."
                    """.formatted(i, i, yamlDoubleQuoted(styleHint)));
                scenes.append("""
                    - scene_id: "S%03d"
                      scene_type: "INT/EXT"
                      location: "Demo location %d"
                      time_of_day: "DAY"
                      characters:
                        - "%s Protagonist"
                        - "Supporting Witness"
                      summary: "Scene %d demonstrates a structured adaptation for %s."
                      dramatic_purpose: "Prove the dev fallback returns schema-valid YAML without a real model."
                      beats:
                        - type: "action"
                          content: "The protagonist enters the scene carrying unresolved pressure from chapter %d."
                        - type: "parenthetical"
                          content: "The supporting witness waits before answering, forcing the protagonist to cross the room."
                        - type: "dialogue"
                          speaker: "Supporting Witness"
                          content: "This is dev fallback output; no real large language model was called."
                        - type: "dialogue"
                          speaker: "%s Protagonist"
                          content: "Then give me the structure first. I will rewrite the truth later."
                    """.formatted(i, i, yamlDoubleQuoted(title), i, yamlDoubleQuoted(targetFormat), i, yamlDoubleQuoted(title)));
            }

            return """
                    schema_version: "1.0"
                    work:
                      title: "%s"
                      original_author: ""
                      language: "%s"
                      source_chapters:
                        count: %d
                        range: "%s"
                    adaptation:
                      target_format: "%s"
                      target_duration: "%s"
                      genre: "%s"
                      tone: "%s"
                      logline: "%s"
                      principles:
                        - "%s"
                        - "%s"
                    characters:
                      - id: "char_001"
                        name: "%s"
                        role: "protagonist"
                        identity: "%s"
                        personality: "%s"
                        goal: "%s"
                        arc: "%s"
                        relationships:
                          - target: "%s"
                            relation: "%s"
                      - id: "char_002"
                        name: "%s"
                        role: "supporting"
                        identity: "%s"
                        personality: "%s"
                        goal: "%s"
                        arc: "%s"
                        relationships:
                          - target: "%s"
                            relation: "%s"
                    plot_outline:
                    %s
                    scenes:
                    %s
                    notes:
                      adaptation_summary: "%s"
                      omitted_elements:
                        - "%s"
                      risks:
                        - "%s"
                      next_steps:
                        - "%s"
                    """.formatted(
                    yamlDoubleQuoted(title),
                    yamlDoubleQuoted(language),
                    sceneCount,
                    yamlDoubleQuoted(chapterRange),
                    yamlDoubleQuoted(targetFormat),
                    yamlDoubleQuoted(targetDuration),
                    yamlDoubleQuoted(english ? "demo" : "演示"),
                    yamlDoubleQuoted(styleHint),
                    yamlDoubleQuoted(english
                            ? "Dev fallback demonstration adaptation for " + title + "."
                            : title + " 的 dev fallback 演示改编。"),
                    yamlDoubleQuoted(english
                            ? "Use parsed chapter count and request metadata."
                            : "使用已解析章节数和请求元数据。"),
                    yamlDoubleQuoted(english
                            ? "Make clear that no real large language model was called."
                            : "明确当前未调用真实大模型。"),
                    yamlDoubleQuoted(english ? title + " Protagonist" : title + "主角"),
                    yamlDoubleQuoted(english ? "demo lead" : "演示主角"),
                    yamlDoubleQuoted(english ? "focused" : "专注"),
                    yamlDoubleQuoted(english
                            ? "Carry the source conflict into a playable scene structure."
                            : "把原文冲突带入可表演场景结构。"),
                    yamlDoubleQuoted(english
                            ? "From source setup to demonstrable screenplay action."
                            : "从原文设定推进到可展示的剧本动作。"),
                    yamlDoubleQuoted(english ? "Supporting Witness" : "辅助证人"),
                    yamlDoubleQuoted(english ? "receives scene pressure from" : "承接来自主角的场景压力"),
                    yamlDoubleQuoted(english ? "Supporting Witness" : "辅助证人"),
                    yamlDoubleQuoted(english ? "demo witness" : "演示证人"),
                    yamlDoubleQuoted(english ? "direct" : "直接"),
                    yamlDoubleQuoted(english
                            ? "Expose the missing real-model configuration."
                            : "暴露当前缺少真实模型配置。"),
                    yamlDoubleQuoted(english
                            ? "Clarifies the fallback state for the user."
                            : "向用户说明 fallback 状态。"),
                    yamlDoubleQuoted(english ? title + " Protagonist" : title + "主角"),
                    yamlDoubleQuoted(english ? "warns" : "提醒"),
                    indent(plotOutline.toString(), 2),
                    indent(scenes.toString(), 2)
                    ,
                    yamlDoubleQuoted(english
                            ? "Generated by dev fallback from request title, target format, style hint, and parsed chapter count."
                            : "由 dev fallback 根据标题、目标格式、风格提示和章节数生成。"),
                    yamlDoubleQuoted(english
                            ? "Detailed model-based literary interpretation."
                            : "真实模型驱动的文学细读。"),
                    yamlDoubleQuoted(english
                            ? "Demo YAML is structural fallback output, not real model output."
                            : "演示 YAML 是结构化 fallback 输出，不是真实模型输出。"),
                    yamlDoubleQuoted(english
                            ? "Configure application-local.yml with a real ChatModel to enable true model conversion."
                            : "配置真实 ChatModel 后启用真实模型转换。")
            );
        }

        private String extractYamlToRepair(String prompt) {
            int marker = prompt.indexOf("YAML to repair:");
            if (marker < 0) {
                return "";
            }
            return prompt.substring(marker + "YAML to repair:".length()).trim();
        }

        private List<Chapter> chapters(String prompt) {
            Matcher matcher = CHAPTER.matcher(prompt);
            List<ChapterStart> starts = new ArrayList<>();
            while (matcher.find()) {
                starts.add(new ChapterStart(Integer.parseInt(matcher.group(1)), matcher.group(2).trim(), matcher.end()));
            }
            List<Chapter> chapters = new ArrayList<>();
            for (int i = 0; i < starts.size(); i++) {
                ChapterStart start = starts.get(i);
                int end = i + 1 < starts.size() ? starts.get(i + 1).lineStart(prompt) : prompt.length();
                chapters.add(new Chapter(start.index(), start.heading(), prompt.substring(start.contentStart(), end).trim()));
            }
            return chapters;
        }

        private int countOccurrences(String text, String needle) {
            int count = 0;
            int index = 0;
            while ((index = text.indexOf(needle, index)) >= 0) {
                count++;
                index += needle.length();
            }
            return count;
        }

        private String value(String prompt, Pattern pattern, String fallback) {
            Matcher matcher = pattern.matcher(prompt);
            if (!matcher.find()) {
                return fallback;
            }
            String value = matcher.group(1).trim();
            return value.isBlank() ? fallback : value;
        }

        private String summary(String content) {
            String normalized = content.replaceAll("\\s+", " ").trim();
            if (normalized.isBlank()) {
                return "A parsed chapter contributes source material for the demo adaptation.";
            }
            return normalized.length() <= 90 ? normalized : normalized.substring(0, 90).trim() + "...";
        }

        private String indent(String value, int spaces) {
            String prefix = " ".repeat(spaces);
            return value.lines()
                    .map(line -> line.isBlank() ? line : prefix + line)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }

        private String json(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private String yamlDoubleQuoted(String value) {
            return json(value).replace("\n", "\\n").replace("\r", "");
        }

        private record Chapter(int index, String heading, String content) {
        }

        private record ChapterStart(int index, String heading, int contentStart) {
            int lineStart(String prompt) {
                int start = prompt.lastIndexOf('\n', contentStart);
                return start < 0 ? 0 : start + 1;
            }
        }
    }
}
