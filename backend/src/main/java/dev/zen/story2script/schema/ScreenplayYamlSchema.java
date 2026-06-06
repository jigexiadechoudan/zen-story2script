package dev.zen.story2script.schema;

import java.util.List;
import java.util.Set;

/**
 * 剧本 YAML Schema v1.0 常量。
 *
 * <p>首版先用代码固化 Schema，不引入完整 JSON Schema 引擎。
 * 这里作为校验器、后续 Schema 接口和工具 Prompt 的单一事实来源。</p>
 */
public final class ScreenplayYamlSchema {

    public static final String VERSION = "1.0";

    /**
     * v1.0 要求的顶层字段。未知顶层字段会被拒绝，避免 Agent 输出漂移，
     * 也方便修复工具按固定结构补齐或归一化。
     */
    public static final List<String> TOP_LEVEL_FIELDS = List.of(
            "schema_version",
            "work",
            "adaptation",
            "characters",
            "plot_outline",
            "scenes",
            "notes"
    );

    /**
     * v1.0 当前支持的场景节拍类型。
     */
    public static final Set<String> BEAT_TYPES = Set.of(
            "action",
            "dialogue",
            "parenthetical",
            "transition"
    );

    /**
     * v1.0 当前支持的场景标题内外景类型。
     */
    public static final Set<String> SCENE_TYPES = Set.of(
            "INT",
            "EXT",
            "INT/EXT"
    );

    private ScreenplayYamlSchema() {
    }
}
