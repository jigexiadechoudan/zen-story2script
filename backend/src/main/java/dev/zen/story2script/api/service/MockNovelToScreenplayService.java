package dev.zen.story2script.api.service;

import dev.zen.story2script.api.dto.ConvertRequest;
import dev.zen.story2script.api.dto.ConvertResponse;
import java.util.List;
import org.springframework.stereotype.Service;

// 临时稳定桩，用于前端联调；真实 Agent 服务就绪后替换这里即可。
@Service
class MockNovelToScreenplayService implements NovelToScreenplayService {

    // 和 /api/schema 返回的版本保持一致，避免前端联调时出现版本漂移。
    private static final String SCHEMA_VERSION = "0.1.0";

    @Override
    public ConvertResponse convert(ConvertRequest request) {
        // styleHint 允许省略；mock 里给默认值，便于前端不传该字段时仍能看到完整响应。
        String styleHint = normalizeOptional(request.styleHint(), "standard");

        // 保持 mock YAML 简单且稳定，方便在 Agent 逻辑完成前验证前端契约。
        String yaml = """
                schemaVersion: "%s"
                title: "%s"
                targetFormat: "%s"
                styleHint: "%s"
                scenes:
                  - id: "scene-1"
                    heading: "INT. MOCK SCENE - DAY"
                    summary: "Mock conversion output for frontend integration."
                    sourceExcerpt: "%s"
                """.formatted(
                SCHEMA_VERSION,
                escapeYamlScalar(request.title()),
                escapeYamlScalar(request.targetFormat()),
                escapeYamlScalar(styleHint),
                escapeYamlScalar(excerpt(request.sourceText()))
        );

        // warnings、qualityReport、agentTrace 都先返回非空结构，减少前端空值分支。
        return new ConvertResponse(
                yaml,
                SCHEMA_VERSION,
                List.of("mock_agent_output"),
                new ConvertResponse.QualityReport(
                        0.5,
                        List.of("request accepted", "mock screenplay YAML generated")
                ),
                new ConvertResponse.AgentTrace(
                        "mock",
                        List.of("received convert request", "returned deterministic mock response")
                )
        );
    }

    //返回风格
    private String normalizeOptional(String value, String fallback) {
        // 可选字符串统一 trim；空白字符串视为未填写。
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String excerpt(String value) {
        // 只返回有限长度的原文预览，避免 mock 响应随长篇文本膨胀。
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }

    private String escapeYamlScalar(String value) {
        // 这里只做 mock 输出所需的标量转义，不替代完整 YAML 校验或序列化。
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
    }
}
