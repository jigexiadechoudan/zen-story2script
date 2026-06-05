package dev.zen.story2script.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 小说转剧本请求体。
 *
 * <p>当前阶段只校验前端联调所需的最小字段完整性：
 * title、sourceText、targetFormat 必填，styleHint 允许为空。
 */
public record ConvertRequest(
        // 作品标题，用于生成 YAML 顶层 title，也方便前端展示任务名称。
        @NotBlank String title,

        // 原始小说文本；这里先只要求非空，长度限制和分段策略留给后续 Agent 层处理。
        @NotBlank String sourceText,

        // 目标输出格式，例如 screenplay；当前 API 只透传，不做格式枚举校验。
        @NotBlank String targetFormat,

        // 风格提示是可选输入；为空时 mock 实现会回落到 standard。
        String styleHint
) {
}
