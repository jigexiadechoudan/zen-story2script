package dev.zen.story2script.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zen.story2script.api.dto.AssistantChatMessage;
import dev.zen.story2script.api.dto.AssistantChatRequest;
import dev.zen.story2script.api.dto.AssistantChatResponse;
import dev.zen.story2script.api.dto.AssistantRefineInputRequest;
import dev.zen.story2script.api.dto.AssistantRefineInputResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InputAssistantService {

    private static final String ASSISTANT_MODEL = "gpt-5.4-mini";
    private static final int SHORT_INPUT_THRESHOLD = 80;
    private static final int MAX_CHAT_MESSAGES = 6;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public InputAssistantService(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider, ObjectMapper objectMapper) {
        this.chatClient = optionalChatClient(chatClientBuilderProvider);
        this.objectMapper = objectMapper;
    }

    InputAssistantService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public AssistantRefineInputResponse refine(AssistantRefineInputRequest request) {
        String rawInput = normalizeText(request.rawInput());
        List<String> styles = normalizeStyles(request.selectedStyles());

        if (chatClient != null) {
            AssistantRefineInputResponse llmResponse = refineWithLlm(rawInput, styles);
            if (llmResponse != null && !llmResponse.enhancedInput().isBlank()) {
                return llmResponse;
            }
        }

        return refineWithRules(rawInput, styles);
    }

    public AssistantChatResponse chat(AssistantChatRequest request) {
        String capability = normalizeCapability(request.capability());
        String homeInput = normalizeText(request.homeInput());
        List<AssistantChatMessage> messages = normalizeMessages(request.messages());
        List<String> styles = normalizeStyles(request.selectedStyles());

        if (chatClient != null) {
            AssistantChatResponse llmResponse = chatWithLlm(capability, homeInput, messages, styles);
            if (llmResponse != null && !llmResponse.assistantMessage().isBlank()) {
                return llmResponse;
            }
        }

        return chatWithRules(capability, homeInput, messages, styles);
    }

    private ChatClient optionalChatClient(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        try {
            ChatClient.Builder chatClientBuilder = chatClientBuilderProvider.getIfAvailable();
            return chatClientBuilder == null ? null : chatClientBuilder.build();
        } catch (BeansException ex) {
            return null;
        }
    }

    private AssistantRefineInputResponse refineWithLlm(String rawInput, List<String> styles) {
        try {
            String content = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(ASSISTANT_MODEL)
                            .temperature(0.2)
                            .build())
                    .system("""
                            你是 Zen Story2Script 首页输入助手，只负责整理用户输入，不生成剧本。
                            目标：把用户原始需求整理成更适合提交给小说转脚本流程的 enhancedInput。
                            约束：
                            1. 不触发 RAG、ReAct 或工具调用。
                            2. 明确区分“用户硬约束”和“风格偏好/软建议”。
                            3. 不改写用户核心意思，不删除关键人物、事件、结局和明确限制。
                            4. 如果信息缺失，只在 suggestions 中提示，不强制补充。
                            5. 只返回 JSON，不要 Markdown 代码块。
                            JSON 字段：
                            enhancedInput: string
                            styleHints: string[]
                            formatHints: {"contentType": "小说转脚本", "tone": string}
                            suggestions: string[]
                            """)
                    .user("""
                            rawInput:
                            %s

                            selectedStyles:
                            %s

                            target:
                            story_to_script_home
                            """.formatted(rawInput, styles))
                    .call()
                    .content();

            return parseLlmResponse(content, styles);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private AssistantChatResponse chatWithLlm(
            String capability,
            String homeInput,
            List<AssistantChatMessage> messages,
            List<String> styles
    ) {
        try {
            String content = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(ASSISTANT_MODEL)
                            .temperature(0.2)
                            .build())
                    .system("""
                            你是 Zen Story2Script 首页输入助手，采用简短聊天对话完成用户选择的能力。
                            能力只可能是：
                            - format：整理用户输入格式
                            - style：给出轻量风格建议
                            如果 capability=format：
                            - assistantMessage 重点说明你如何把输入拆成目标、硬约束、素材、待补充项。
                            - enhancedInput 必须突出“用户硬约束”和“整理后的素材”，风格只作为可选项。
                            - suggestions 优先提示标题、角色、章节、字数、缺失约束。
                            如果 capability=style：
                            - assistantMessage 重点给出 1-2 条风格方向建议和原因。
                            - enhancedInput 必须保留用户素材，同时加入“风格偏好/软建议”段落。
                            - suggestions 优先提示节奏、情绪、视听感、类型标签，不要求用户补齐结构。
                            你必须直接基于当前对话回复，不生成剧本，不触发 RAG、ReAct 或任何工具调用。
                            保持上下文短：assistantMessage 最多 2 句，suggestions 最多 3 条。
                            始终生成当前 best-effort enhancedInput，供用户确认后回填首页输入区。
                            不改写用户核心意思；明确区分用户硬约束和风格偏好/软建议。
                            只返回 JSON，不要 Markdown 代码块。
                            JSON 字段：
                            assistantMessage: string
                            enhancedInput: string
                            styleHints: string[]
                            formatHints: {"contentType": "小说转脚本", "tone": string}
                            suggestions: string[]
                            """)
                    .user("""
                            capability:
                            %s

                            homeInput:
                            %s

                            selectedStyles:
                            %s

                            recentMessages:
                            %s
                            """.formatted(capability, homeInput, styles, formatMessages(messages)))
                    .call()
                    .content();

            return parseChatResponse(content, capability, homeInput, messages, styles);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private AssistantChatResponse parseChatResponse(
            String content,
            String capability,
            String homeInput,
            List<AssistantChatMessage> messages,
            List<String> fallbackStyles
    ) {
        if (content == null || content.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(extractJsonObject(content), new TypeReference<>() {
            });
            String assistantMessage = String.valueOf(payload.getOrDefault("assistantMessage", "")).trim();
            String enhancedInput = String.valueOf(payload.getOrDefault("enhancedInput", "")).trim();
            if (assistantMessage.isBlank()) {
                return null;
            }
            if (enhancedInput.isBlank()) {
                enhancedInput = chatWithRules(capability, homeInput, messages, fallbackStyles).enhancedInput();
            }

            Object rawStyleHints = payload.get("styleHints") == null ? fallbackStyles : payload.get("styleHints");
            Object rawFormatHints = payload.get("formatHints") == null ? Map.of() : payload.get("formatHints");
            Object rawSuggestions = payload.get("suggestions") == null ? List.of() : payload.get("suggestions");
            List<String> styleHints = objectMapper.convertValue(rawStyleHints, STRING_LIST)
                    .stream()
                    .map(this::normalizeInline)
                    .filter(style -> !style.isBlank())
                    .toList();
            Map<String, String> formatHints = withDefaultFormatHints(
                    objectMapper.convertValue(rawFormatHints, STRING_MAP),
                    styleHints
            );
            List<String> suggestions = objectMapper.convertValue(rawSuggestions, STRING_LIST)
                    .stream()
                    .map(this::normalizeInline)
                    .filter(suggestion -> !suggestion.isBlank())
                    .limit(3)
                    .toList();

            return new AssistantChatResponse(
                    assistantMessage,
                    enhancedInput,
                    List.copyOf(styleHints),
                    formatHints,
                    suggestions.isEmpty() ? List.of("可以继续补充标题、角色或章节数量") : List.copyOf(suggestions)
            );
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            return null;
        }
    }

    private AssistantRefineInputResponse parseLlmResponse(String content, List<String> fallbackStyles) {
        if (content == null || content.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(extractJsonObject(content), new TypeReference<>() {
            });
            String enhancedInput = String.valueOf(payload.getOrDefault("enhancedInput", "")).trim();
            if (enhancedInput.isBlank()) {
                return null;
            }

            Object rawStyleHints = payload.get("styleHints") == null ? fallbackStyles : payload.get("styleHints");
            Object rawFormatHints = payload.get("formatHints") == null ? Map.of() : payload.get("formatHints");
            Object rawSuggestions = payload.get("suggestions") == null ? List.of() : payload.get("suggestions");

            List<String> styleHints = objectMapper.convertValue(rawStyleHints, STRING_LIST)
                    .stream()
                    .map(this::normalizeInline)
                    .filter(style -> !style.isBlank())
                    .toList();
            Map<String, String> formatHints = objectMapper.convertValue(rawFormatHints, STRING_MAP);
            List<String> suggestions = objectMapper.convertValue(rawSuggestions, STRING_LIST)
                    .stream()
                    .map(this::normalizeInline)
                    .filter(suggestion -> !suggestion.isBlank())
                    .toList();

            formatHints = withDefaultFormatHints(formatHints, styleHints);

            return new AssistantRefineInputResponse(
                    enhancedInput,
                    List.copyOf(styleHints),
                    Map.copyOf(formatHints),
                    suggestions.isEmpty() ? List.of("当前输入已经比较清晰，可以直接提交生成") : List.copyOf(suggestions)
            );
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            return null;
        }
    }

    private AssistantChatResponse chatWithRules(
            String capability,
            String homeInput,
            List<AssistantChatMessage> messages,
            List<String> styles
    ) {
        String rawInput = mergedChatInput(homeInput, messages);
        AssistantRefineInputResponse refined = "style".equals(capability)
                ? refineStyleWithRules(rawInput, styles)
                : refineFormatWithRules(rawInput, styles);
        String assistantMessage = "style".equals(capability)
                ? "我会从类型、情绪和视听感给出轻量风格建议，并把它们作为软偏好写进草稿。"
                : "我会把输入整理成目标、用户硬约束、素材和待补充项，方便直接回填首页。";
        return new AssistantChatResponse(
                assistantMessage,
                refined.enhancedInput(),
                refined.styleHints(),
                refined.formatHints(),
                refined.suggestions().stream().limit(3).toList()
        );
    }

    private String mergedChatInput(String homeInput, List<AssistantChatMessage> messages) {
        List<String> parts = new ArrayList<>();
        if (!homeInput.isBlank()) {
            parts.add("首页已有输入：\n" + homeInput);
        }
        List<String> userMessages = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(AssistantChatMessage::text)
                .filter(text -> !text.isBlank())
                .toList();
        if (!userMessages.isEmpty()) {
            parts.add("用户最近补充：\n" + String.join("\n", userMessages));
        }
        return String.join("\n\n", parts).trim();
    }

    private String formatMessages(List<AssistantChatMessage> messages) {
        return messages.stream()
                .map(message -> "%s: %s".formatted(message.role(), message.text()))
                .toList()
                .toString();
    }

    private List<AssistantChatMessage> normalizeMessages(List<AssistantChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(message -> message != null && message.text() != null && !message.text().isBlank())
                .map(message -> new AssistantChatMessage(normalizeRole(message.role()), normalizeText(message.text())))
                .limit(MAX_CHAT_MESSAGES)
                .toList();
    }

    private String normalizeRole(String role) {
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private String normalizeCapability(String capability) {
        return "style".equalsIgnoreCase(capability) ? "style" : "format";
    }

    private Map<String, String> withDefaultFormatHints(Map<String, String> formatHints, List<String> styleHints) {
        Map<String, String> normalized = new java.util.LinkedHashMap<>(formatHints == null ? Map.of() : formatHints);
        normalized.putIfAbsent("contentType", "小说转脚本");
        normalized.putIfAbsent("tone", styleHints.isEmpty() ? "未指定" : String.join("、", styleHints));
        return Map.copyOf(normalized);
    }

    private String extractJsonObject(String content) {
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private AssistantRefineInputResponse refineWithRules(String rawInput, List<String> styles) {
        String tone = styles.isEmpty() ? "未指定" : String.join("、", styles);

        return new AssistantRefineInputResponse(
                buildEnhancedInput(rawInput, styles),
                styles,
                Map.of(
                        "contentType", "小说转脚本",
                        "tone", tone
                ),
                buildSuggestions(rawInput, styles)
        );
    }

    private AssistantRefineInputResponse refineFormatWithRules(String rawInput, List<String> styles) {
        String enhancedInput = String.join("\n\n",
                "【改编目标】小说转脚本",
                "【用户硬约束】\n" + rawInput,
                styles.isEmpty() ? "【风格偏好】未指定。可后续补充，这是软建议。" : "【风格偏好】" + String.join("、", styles) + "。这是软建议，不要覆盖用户硬约束。",
                "【整理后的素材要求】保留用户原始人物、事件、结局和明确限制；未说明的信息只做合理补足，不强制改变原意。",
                "【待补充项】如有需要，可补充标题、主要角色、章节数量或目标时长。"
        );
        return new AssistantRefineInputResponse(
                enhancedInput,
                styles,
                Map.of("contentType", "小说转脚本", "tone", styles.isEmpty() ? "未指定" : String.join("、", styles)),
                buildFormatSuggestions(rawInput)
        );
    }

    private AssistantRefineInputResponse refineStyleWithRules(String rawInput, List<String> styles) {
        List<String> styleHints = styles.isEmpty() ? inferStyleHints(rawInput) : styles;
        String enhancedInput = String.join("\n\n",
                "【改编目标】小说转脚本",
                "【用户硬约束】\n" + rawInput,
                "【风格偏好/软建议】" + String.join("、", styleHints) + "。请只作为语气、节奏和视听呈现参考，不改变核心人物、事件和结局。",
                "【风格执行方向】优先保持故事原意；通过节奏、场面调度、对白密度和情绪色彩体现风格。"
        );
        return new AssistantRefineInputResponse(
                enhancedInput,
                styleHints,
                Map.of("contentType", "小说转脚本", "tone", String.join("、", styleHints)),
                buildStyleSuggestions(rawInput, styleHints)
        );
    }

    private List<String> buildFormatSuggestions(String rawInput) {
        List<String> suggestions = new ArrayList<>();
        if (!containsAny(rawInput, "《", "标题", "题名", "片名", "书名") && !rawInput.toLowerCase(Locale.ROOT).contains("title")) {
            suggestions.add("可以补充作品标题");
        }
        if (!containsAny(rawInput, "主角", "男主", "女主", "角色", "人物", "反派", "配角", "protagonist", "character")) {
            suggestions.add("可以补充主要角色");
        }
        if (!hasChapterSignal(rawInput)) {
            suggestions.add("可以指定章节数量");
        }
        return suggestions.isEmpty() ? List.of("当前格式已经比较清晰，可以继续补充正文素材") : List.copyOf(suggestions);
    }

    private List<String> buildStyleSuggestions(String rawInput, List<String> styleHints) {
        List<String> suggestions = new ArrayList<>();
        if (styleHints.isEmpty() || "未指定".equals(String.join("", styleHints))) {
            suggestions.add("可以选择悬疑、治愈、电影感或短剧感作为风格方向");
        }
        if (!containsAny(rawInput, "节奏", "快节奏", "慢节奏", "紧凑", "舒缓")) {
            suggestions.add("可以补充节奏偏好，例如紧凑或舒缓");
        }
        if (!containsAny(rawInput, "情绪", "温暖", "压抑", "幽默", "冷峻", "浪漫")) {
            suggestions.add("可以补充情绪基调，例如温暖、压抑或轻松");
        }
        return suggestions.isEmpty() ? List.of("当前风格方向已经可用，可以继续补充视听参考") : List.copyOf(suggestions);
    }

    private List<String> inferStyleHints(String rawInput) {
        if (containsAny(rawInput, "悬疑", "失踪", "调查", "真相", "案件")) {
            return List.of("悬疑", "电影感");
        }
        if (containsAny(rawInput, "治愈", "成长", "亲情", "和解", "温暖")) {
            return List.of("治愈", "电影感");
        }
        return List.of("电影感");
    }

    private String buildEnhancedInput(String rawInput, List<String> styles) {
        List<String> sections = new ArrayList<>();
        sections.add("【改编目标】小说转脚本");

        if (rawInput.length() < SHORT_INPUT_THRESHOLD) {
            sections.add("【需求整理】请基于以下原始想法扩展为适合改编的故事素材，保持用户核心意思不变。");
        } else {
            sections.add("【需求整理】请基于以下原始素材进行剧本改编，保留用户已写明的关键情节和限制。");
        }

        sections.add("【用户硬约束】\n" + rawInput);

        if (!styles.isEmpty()) {
            sections.add("【风格偏好】" + String.join("、", styles) + "。这是软建议，请在不改变核心人物、事件、结局和明确限制的前提下使用。");
        }

        sections.add("【提交说明】请优先遵守用户硬约束；标题、章节数量、角色设定等未写明的信息可以合理补足，但不要强制改变原意。");

        return String.join("\n\n", sections);
    }

    private List<String> buildSuggestions(String rawInput, List<String> styles) {
        List<String> suggestions = new ArrayList<>();
        String lowerInput = rawInput.toLowerCase(Locale.ROOT);

        if (!containsAny(rawInput, "《", "标题", "题名", "片名", "书名") && !lowerInput.contains("title")) {
            suggestions.add("可以补充作品标题");
        }
        if (!containsAny(rawInput, "主角", "男主", "女主", "角色", "人物", "反派", "配角", "protagonist", "character")) {
            suggestions.add("可以补充主要角色");
        }
        if (!hasChapterSignal(rawInput)) {
            suggestions.add("可以指定章节数量");
        }
        if (styles.isEmpty() && !containsAny(rawInput, "风格", "基调", "悬疑", "治愈", "电影感", "短剧感", "轻喜剧", "赛博朋克", "tone", "style")) {
            suggestions.add("可以选择或描述风格偏好");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("当前输入已经比较清晰，可以直接提交生成");
        }

        return List.copyOf(suggestions);
    }

    private List<String> normalizeStyles(List<String> selectedStyles) {
        if (selectedStyles == null || selectedStyles.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> styles = new LinkedHashSet<>();
        for (String style : selectedStyles) {
            String normalized = normalizeInline(style);
            if (!normalized.isBlank()) {
                styles.add(normalized);
            }
        }
        return List.copyOf(styles);
    }

    private String normalizeText(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{3,}", "\n\n");
    }

    private String normalizeInline(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean hasChapterSignal(String rawInput) {
        String lowerInput = rawInput.toLowerCase(Locale.ROOT);
        return lowerInput.contains("chapter")
                || containsAny(rawInput, "第1章", "第一章", "第1集", "第一集", "三章", "3章", "章节", "集数");
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
