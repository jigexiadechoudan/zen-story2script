package dev.zen.story2script.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YamlSchemaValidatorTests {

    private final YamlSchemaValidator validator = new YamlSchemaValidator();

    /**
     * 覆盖完整 v1.0 结构：顶层字段、列表字段、场景类型和 beat 类型都合法时应通过。
     */
    @Test
    void validYamlPasses() {
        YamlSchemaValidationResult result = validator.validate(validYaml());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.document()).containsEntry("schema_version", "1.0");
    }

    /**
     * scenes 是后续剧本渲染和导出的核心列表，缺失时必须失败并定位到顶层字段。
     */
    @Test
    void missingScenesFails() {
        String yaml = validYaml().replace("""
                scenes:
                  - scene_id: "S001"
                    scene_type: "EXT"
                    location: "Town Station"
                    time_of_day: "NIGHT"
                    characters:
                      - "Lin Xia"
                    summary: "Lin Xia returns to town."
                    dramatic_purpose: "Open the mystery."
                    beats:
                      - type: "action"
                        content: "Fog covers the platform."
                      - type: "dialogue"
                        speaker: "Chen Mo"
                        content: "You should not have come back."
                """, "");

        YamlSchemaValidationResult result = validator.validate(yaml);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> {
            assertThat(error.path()).isEqualTo("$.scenes");
            assertThat(error.code()).isEqualTo("MISSING_TOP_LEVEL_FIELD");
        });
    }

    /**
     * beat type 只允许动作、对白、括号提示和转场，非法类型应返回可定位的枚举错误。
     */
    @Test
    void invalidBeatTypeFails() {
        String yaml = validYaml().replace("type: \"action\"", "type: \"voiceover\"");

        YamlSchemaValidationResult result = validator.validate(yaml);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> {
            assertThat(error.path()).isEqualTo("$.scenes[0].beats[0].type");
            assertThat(error.code()).isEqualTo("ENUM_VALUE_INVALID");
        });
    }

    /**
     * scene_type 只允许 INT、EXT、INT/EXT，避免模型输出自然语言或非标准场景类型。
     */
    @Test
    void invalidSceneTypeFails() {
        String yaml = validYaml().replace("scene_type: \"EXT\"", "scene_type: \"INTERIOR\"");

        YamlSchemaValidationResult result = validator.validate(yaml);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> {
            assertThat(error.path()).isEqualTo("$.scenes[0].scene_type");
            assertThat(error.code()).isEqualTo("ENUM_VALUE_INVALID");
        });
    }

    /**
     * 测试用最小合法样例，内容字段保持简单，重点验证结构而不是文学质量。
     */
    private String validYaml() {
        return """
                schema_version: "1.0"
                work:
                  title: "Fog Town Letter"
                  original_author: ""
                  language: "zh-CN"
                  source_chapters:
                    count: 3
                    range: "Chapter 1-3"
                adaptation:
                  target_format: "short_drama"
                  target_duration: "10-15min"
                  genre: "mystery"
                  tone: "restrained"
                  logline: "A reporter returns home after receiving an anonymous letter."
                  principles:
                    - "Keep the main mystery."
                characters:
                  - id: "char_001"
                    name: "Lin Xia"
                    role: "protagonist"
                    identity: "reporter"
                    personality: "controlled"
                    goal: "Find the truth."
                    arc: "From avoidance to confrontation."
                    relationships:
                      - target: "Chen Mo"
                        relation: "old friend"
                plot_outline:
                  - source_chapter: "Chapter 1"
                    key_events:
                      - "Lin Xia receives an anonymous letter."
                    adaptation_choice: "Externalize inner monologue into action."
                scenes:
                  - scene_id: "S001"
                    scene_type: "EXT"
                    location: "Town Station"
                    time_of_day: "NIGHT"
                    characters:
                      - "Lin Xia"
                    summary: "Lin Xia returns to town."
                    dramatic_purpose: "Open the mystery."
                    beats:
                      - type: "action"
                        content: "Fog covers the platform."
                      - type: "dialogue"
                        speaker: "Chen Mo"
                        content: "You should not have come back."
                notes:
                  adaptation_summary: "The opening chapters are compressed into one scene."
                  omitted_elements:
                    - "Minor backstory."
                  risks:
                    - "Some motives need polish."
                  next_steps:
                    - "Add a stronger ending hook."
                """;
    }
}
