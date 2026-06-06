package dev.zen.story2script.schema;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 SnakeYAML 的剧本 YAML Schema v1.0 校验器。
 *
 * <p>这里刻意只覆盖 MVP 需要的结构校验：YAML 语法、顶层字段契约、
 * 必需列表字段和 v1.0 枚举。剧本文学质量评分留给后续质量评分层扩展。</p>
 */
public class YamlSchemaValidator {

    private final Yaml yaml;

    /**
     * 使用 SafeConstructor 构造 SnakeYAML 解析器，避免解析过程中实例化任意 Java 类型。
     * 当前校验只需要 YAML 到基础 Map/List/Scalar 的转换。
     */
    public YamlSchemaValidator() {
        this.yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    /**
     * 解析并校验一段剧本 YAML。
     *
     * <p>返回值不会抛出业务校验异常，而是统一收敛到 {@link YamlSchemaValidationResult}。
     * 这样 Agent、YamlValidationTool 或后续 YamlRepairTool 可以在同一条链路里读取错误列表，
     * 决定是修复、重试，还是把错误返回给调用方。</p>
     *
     * @param yamlText 待校验的 YAML 文本，不能为 null 或空白
     * @return 包含是否通过、解析后文档和结构化错误列表的校验结果
     */
    public YamlSchemaValidationResult validate(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            return YamlSchemaValidationResult.invalid(Map.of(), List.of(error(
                    "$",
                    "YAML_EMPTY",
                    "YAML content must not be empty."
            )));
        }

        // 第一阶段：安全解析 YAML，并把解析失败归一成与结构校验一致的错误契约。
        Object loaded;
        try {
            loaded = yaml.load(yamlText);
        } catch (YAMLException ex) {
            return YamlSchemaValidationResult.invalid(Map.of(), List.of(error(
                    "$",
                    "YAML_PARSE_FAILED",
                    "YAML content could not be parsed: " + ex.getMessage()
            )));
        }

        // v1.0 要求根节点是对象。标量或列表 YAML 语法上可能合法，
        // 但不能表达剧本草稿结构。
        if (!(loaded instanceof Map<?, ?> rawDocument)) {
            return YamlSchemaValidationResult.invalid(Map.of(), List.of(error(
                    "$",
                    "ROOT_TYPE_INVALID",
                    "YAML root must be an object."
            )));
        }

        Map<String, Object> document = toStringKeyMap(rawDocument);
        List<YamlSchemaValidationError> errors = new ArrayList<>();

        // 第二阶段：执行 v1.0 结构校验。显式写出规则，便于后续版本扩展字段，
        // 不把首版行为藏进通用引擎里。
        validateTopLevelFields(document, errors);
        validateSchemaVersion(document, errors);
        validateObjectField(document, "work", errors);
        validateObjectField(document, "adaptation", errors);
        validateObjectField(document, "notes", errors);
        validateListField(document, "characters", errors);
        validateListField(document, "plot_outline", errors);
        validateScenes(document, errors);

        if (errors.isEmpty()) {
            return YamlSchemaValidationResult.valid(document);
        }
        return YamlSchemaValidationResult.invalid(document, errors);
    }

    /**
     * 校验顶层字段集合。
     *
     * <p>v1.0 同时做“缺失字段”和“未知字段”检查：前者保证下游工具总能找到核心区块，
     * 后者防止模型输出自由扩展字段导致前端、导出器或修复工具处理不一致。</p>
     */
    private void validateTopLevelFields(Map<String, Object> document, List<YamlSchemaValidationError> errors) {
        // 缺失的必需字段逐项报告，修复工具可以只补对应缺失区块。
        for (String field : ScreenplayYamlSchema.TOP_LEVEL_FIELDS) {
            if (!document.containsKey(field)) {
                errors.add(error(
                        "$." + field,
                        "MISSING_TOP_LEVEL_FIELD",
                        "Top-level field '" + field + "' is required."
                ));
            }
        }

        // v1.0 拒绝未知顶层字段，保持 Agent 输出稳定。
        for (String field : document.keySet()) {
            if (!ScreenplayYamlSchema.TOP_LEVEL_FIELDS.contains(field)) {
                errors.add(error(
                        "$." + field,
                        "UNKNOWN_TOP_LEVEL_FIELD",
                        "Top-level field '" + field + "' is not part of schema v1.0."
                ));
            }
        }
    }

    /**
     * 校验 schema_version 是否严格等于当前固化版本。
     *
     * <p>这里使用字符串比较，允许 YAML 解析后传入的标量先转成字符串；
     * 但最终值必须是 {@code 1.0}，避免不同版本规则混用。</p>
     */
    private void validateSchemaVersion(Map<String, Object> document, List<YamlSchemaValidationError> errors) {
        Object version = document.get("schema_version");
        if (version == null) {
            return;
        }
        if (!ScreenplayYamlSchema.VERSION.equals(String.valueOf(version))) {
            errors.add(error(
                    "$.schema_version",
                    "SCHEMA_VERSION_INVALID",
                    "schema_version must be '" + ScreenplayYamlSchema.VERSION + "'."
            ));
        }
    }

    /**
     * 校验顶层对象字段的类型。
     *
     * <p>字段缺失由 {@link #validateTopLevelFields(Map, List)} 负责报告，
     * 这里只处理字段存在但类型不符合的情况，避免同一问题重复报错。</p>
     */
    private void validateObjectField(
            Map<String, Object> document,
            String field,
            List<YamlSchemaValidationError> errors
    ) {
        if (!document.containsKey(field) || document.get(field) == null) {
            return;
        }
        if (!(document.get(field) instanceof Map<?, ?>)) {
            errors.add(error(
                    "$." + field,
                    "FIELD_TYPE_INVALID",
                    "Field '" + field + "' must be an object."
            ));
        }
    }

    /**
     * 校验顶层列表字段的类型，并把合法列表返回给后续深层校验。
     *
     * <p>如果字段不存在，说明缺失错误已经由顶层字段校验产生，这里返回空列表让调用方继续收集其他错误。
     * 如果字段存在但不是列表，会记录类型错误并返回空列表，避免后续强转失败。</p>
     */
    private List<?> validateListField(
            Map<String, Object> document,
            String field,
            List<YamlSchemaValidationError> errors
    ) {
        if (!document.containsKey(field)) {
            return List.of();
        }

        Object value = document.get(field);
        if (!(value instanceof List<?> list)) {
            errors.add(error(
                    "$." + field,
                    "FIELD_TYPE_INVALID",
                    "Field '" + field + "' must be a list."
            ));
            return List.of();
        }
        return list;
    }

    /**
     * 校验 scenes 列表以及每个场景内部的关键结构。
     *
     * <p>v1.0 不校验 scene_id、location、summary 等文学或业务完整度字段，
     * 只校验会影响脚本结构化处理的 scene_type 和 beats。</p>
     */
    private void validateScenes(Map<String, Object> document, List<YamlSchemaValidationError> errors) {
        List<?> scenes = validateListField(document, "scenes", errors);
        // 场景和节拍路径包含数组下标，方便调用方把修复提示定位到具体节点。
        for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++) {
            Object scene = scenes.get(sceneIndex);
            String scenePath = "$.scenes[" + sceneIndex + "]";
            if (!(scene instanceof Map<?, ?> sceneMap)) {
                errors.add(error(
                        scenePath,
                        "LIST_ITEM_TYPE_INVALID",
                        "Each scenes item must be an object."
                ));
                continue;
            }

            validateSceneType(sceneMap, scenePath, errors);
            validateBeats(sceneMap, scenePath, errors);
        }
    }

    /**
     * 校验单个场景的内外景类型。
     *
     * <p>scene_type 是剧本场景标题的核心字段，首版只允许 INT、EXT、INT/EXT。
     * 缺失和非法枚举分别给出不同错误码，方便修复工具区分“补字段”和“改值”。</p>
     */
    private void validateSceneType(
            Map<?, ?> sceneMap,
            String scenePath,
            List<YamlSchemaValidationError> errors
    ) {
        Object sceneType = sceneMap.get("scene_type");
        if (sceneType == null) {
            errors.add(error(
                    scenePath + ".scene_type",
                    "MISSING_FIELD",
                    "Field 'scene_type' is required for each scene."
            ));
            return;
        }

        String sceneTypeValue = String.valueOf(sceneType);
        if (!ScreenplayYamlSchema.SCENE_TYPES.contains(sceneTypeValue)) {
            errors.add(error(
                    scenePath + ".scene_type",
                    "ENUM_VALUE_INVALID",
                    "scene_type must be one of " + ScreenplayYamlSchema.SCENE_TYPES + "."
            ));
        }
    }

    /**
     * 校验单个场景的 beats 列表和每个 beat 的 type。
     *
     * <p>beats 决定场景内部动作、对白、括号提示和转场的顺序，是后续渲染、
     * 导出或人工编辑最依赖的结构，所以这里比其他场景字段更严格。</p>
     */
    private void validateBeats(
            Map<?, ?> sceneMap,
            String scenePath,
            List<YamlSchemaValidationError> errors
    ) {
        Object beats = sceneMap.get("beats");
        if (!(beats instanceof List<?> beatList)) {
            errors.add(error(
                    scenePath + ".beats",
                    "FIELD_TYPE_INVALID",
                    "Field 'beats' must be a list for each scene."
            ));
            return;
        }

        for (int beatIndex = 0; beatIndex < beatList.size(); beatIndex++) {
            Object beat = beatList.get(beatIndex);
            String beatPath = scenePath + ".beats[" + beatIndex + "]";
            if (!(beat instanceof Map<?, ?> beatMap)) {
                errors.add(error(
                        beatPath,
                        "LIST_ITEM_TYPE_INVALID",
                        "Each beats item must be an object."
                ));
                continue;
            }

            Object type = beatMap.get("type");
            if (type == null) {
                errors.add(error(
                        beatPath + ".type",
                        "MISSING_FIELD",
                        "Field 'type' is required for each beat."
                ));
                continue;
            }

            String typeValue = String.valueOf(type);
            if (!ScreenplayYamlSchema.BEAT_TYPES.contains(typeValue)) {
                errors.add(error(
                        beatPath + ".type",
                        "ENUM_VALUE_INVALID",
                        "beats[].type must be one of " + ScreenplayYamlSchema.BEAT_TYPES + "."
                ));
            }
        }
    }

    /**
     * 将 SnakeYAML 返回的任意 key 类型 Map 归一为 String key。
     *
     * <p>YAML 允许非字符串 key，但本项目 Schema 的字段名都是字符串。
     * 归一后可以稳定生成 {@code $.field} 形式的错误路径。</p>
     */
    private Map<String, Object> toStringKeyMap(Map<?, ?> rawDocument) {
        Map<String, Object> document = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawDocument.entrySet()) {
            document.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return document;
    }

    /**
     * 创建统一的结构化错误对象。
     *
     * <p>保留这个小方法是为了让错误格式集中，后续如果要增加严重级别、
     * 修复建议或原始值，也可以从这里统一调整。</p>
     */
    private YamlSchemaValidationError error(String path, String code, String message) {
        return new YamlSchemaValidationError(path, code, message);
    }
}
