package dev.zen.story2script.api.dto;

import java.util.List;

/**
 * 小说转剧本响应体。
 *
 * <p>字段保持和前端约定一致，即使当前由 mock Agent 返回，也保留后续真实 Agent
 * 需要填充的质量报告和执行轨迹位置。
 */
public record ConvertResponse(
        // 剧本 YAML 文本；当前阶段是 mock 输出，后续应由 Agent 生成并由 schema 层校验。
        String yaml,

        // YAML 契约版本，便于前端根据版本做兼容处理。
        String schemaVersion,

        // 非阻断警告，例如 mock 输出、降级处理或质量提示。
        List<String> warnings,

        // 质量报告用于承载结构化检查结果，不与 warnings 混在一起。
        QualityReport qualityReport,

        // Agent 执行轨迹用于调试和前端展示，不要求包含 ReAct 细节。
        AgentTrace agentTrace
) {

    /**
     * 轻量质量报告。
     *
     * <p>confidence 当前只表达 mock 可信度占位，不代表真实模型评分。
     */
    public record QualityReport(
            // 0 到 1 的置信度占位值；真实评分策略后续再定义。
            double confidence,

            // 已执行或已跳过的检查项说明，方便前端直接展示。
            List<String> checks
    ) {
    }

    /**
     * Agent 执行轨迹。
     *
     * <p>mode 用来区分 mock、real、fallback 等执行模式，steps 保留粗粒度流程记录。
     */
    public record AgentTrace(
            // 当前执行模式；mock 实现固定返回 mock。
            String mode,

            // 粗粒度步骤列表，避免在 API 层绑定具体 ReAct 内部结构。
            List<String> steps
    ) {
    }
}
