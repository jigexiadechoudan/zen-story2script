---
doc_id: scene_outline_knowledge
title: "分场大纲改编知识"
target_format: "scene_outline"
knowledge_type: "adaptation_guidance"
language: "zh-CN"
version: "0.2.0"
last_reviewed: "2026-06-06"
recommended_splitter: "chunk_boundary_comment"
fallback_splitter: "h2"
chunk_boundary_start: "<!-- chunk:start"
chunk_boundary_end: "<!-- chunk:end -->"
source_urls:
  - "https://en.wikipedia.org/wiki/Step_outline"
  - "https://www.writerduet.com/blog/how-pro-screenwriters-outline-a-script/"
  - "https://screenplay.com/pages/basic-screenplay-format"
---

# 分场大纲改编知识

## 文档切片契约

本文件面向 RAG ingestion。推荐按 `<!-- chunk:start ... -->` 到 `<!-- chunk:end -->` 切片。每个 chunk 都应作为独立知识单元入库，并继承 front matter 中的 `doc_id`、`target_format`、`language`、`source_urls`。

固定字段：`chunk_id`、`chunk_type`、`retrieval_terms`、`prompt_slots`、`summary`、`guidance`、`checks`。

<!-- chunk:start id="scene_outline.intent" target_format="scene_outline" chunk_type="intent" -->
## scene_outline.intent

**metadata**

- chunk_id: `scene_outline.intent`
- target_format: `scene_outline`
- chunk_type: `intent`
- retrieval_terms: `分场大纲`, `step outline`, `场景蓝图`, `逐场拆解`, `可扩写`
- prompt_slots: `scene_planning`, `yaml_writing`

**summary**

分场大纲是可扩写的场景蓝图，不是完整剧本，也不是剧情简介。

**guidance**

生成 `scene_outline` 时，应重点回答：每场发生什么、谁推动、冲突是什么、信息如何变化、下一场为什么必须发生。

分场大纲应比梗概更具体，但比剧本更简洁。可以包含关键对白意图或动作提示，但不应写成长篇对白稿。

**prompt_notes**

可注入提示：用场景功能和因果推进组织内容，不要写完整对白，不要只写剧情简介。

**checks**

- 是否逐场拆解而非整体梗概。
- 每场是否能继续扩写成剧本。
- 是否避免完整对白化。
- 场与场之间是否有因果关系。
<!-- chunk:end -->

<!-- chunk:start id="scene_outline.scene_unit" target_format="scene_outline" chunk_type="structure" -->
## scene_outline.scene_unit

**metadata**

- chunk_id: `scene_outline.scene_unit`
- target_format: `scene_outline`
- chunk_type: `structure`
- retrieval_terms: `分场条目`, `sceneId`, `场景目标`, `冲突`, `转折`, `来源章节`
- prompt_slots: `scene_planning`, `yaml_writing`

**summary**

一个分场条目应包含场号、地点时间、人物、目标、冲突、关键行动、转折结果和来源章节。

**guidance**

一个分场条目建议包含：

- 场号或 sceneId。
- 内/外景、地点、时间。
- 出场人物。
- 场景目标：这场戏要解决或制造什么问题。
- 冲突：人物之间或人物与环境之间的阻力。
- 关键行动：2 到 4 个主要 beats。
- 转折或结果：场景结束时故事状态的变化。
- 与原文来源章节的对应关系。

如果一个小说章节包含多个重要冲突，应拆成多个分场；如果多个章节只服务同一冲突，应合并为一个分场。

**prompt_notes**

可注入提示：每个 scene 的 summary 必须写清目标、冲突和结果；sourceChapters 要尽量保留原文依据。

**checks**

- 是否包含目标、冲突和结果。
- 是否保留来源章节。
- 是否把多冲突章节拆开。
- 是否把重复功能章节合并。
<!-- chunk:end -->

<!-- chunk:start id="scene_outline.granularity" target_format="scene_outline" chunk_type="granularity" -->
## scene_outline.granularity

**metadata**

- chunk_id: `scene_outline.granularity`
- target_format: `scene_outline`
- chunk_type: `granularity`
- retrieval_terms: `分场粒度`, `拆场`, `合场`, `场景长度`, `80到180字`
- prompt_slots: `scene_planning`

**summary**

分场粒度应介于梗概和剧本初稿之间，服务后续扩写。

**guidance**

推荐每场用 80 到 180 字左右描述核心戏剧功能，并保留少量可扩写信息。太粗会变成梗概，太细会变成剧本初稿。

判断是否应拆场：

- 地点或时间明显变化。
- 主要冲突对象变化。
- 人物目标变化。
- 新信息导致行动方向变化。
- 场景已经出现清晰转折，下一段应承担新功能。

**prompt_notes**

可注入提示：按“地点/时间/目标/冲突/转折”判断拆场，不要机械按原文章节切。

**checks**

- 每场粒度是否适合后续扩写。
- 是否避免过粗梗概化。
- 是否避免过细对白化。
- 拆场依据是否清楚。
<!-- chunk:end -->

<!-- chunk:start id="scene_outline.dramatic_function" target_format="scene_outline" chunk_type="dramatic_function" -->
## scene_outline.dramatic_function

**metadata**

- chunk_id: `scene_outline.dramatic_function`
- target_format: `scene_outline`
- chunk_type: `dramatic_function`
- retrieval_terms: `戏剧功能`, `人物目标`, `秘密`, `误会`, `升级冲突`, `选择`, `失败`
- prompt_slots: `scene_planning`, `yaml_writing`

**summary**

每个分场都应有明确戏剧功能，避免空泛表达。

**guidance**

常见场景功能包括：

- 建立人物目标。
- 暴露秘密或信息差。
- 制造误会。
- 升级冲突。
- 迫使人物做选择。
- 让计划失败。
- 改变人物关系。
- 给出阶段性代价或胜利。

生成时避免“主角继续调查”“两人发生争执”这类空泛表达。应写明调查发现什么、争执围绕什么、争执后谁获得优势。

**prompt_notes**

可注入提示：每个 scene 至少标明一个戏剧功能，且功能必须带来信息、关系、目标或风险变化。

**checks**

- 场景功能是否明确。
- 是否避免空泛动作。
- 是否写清优势变化。
- 是否产生信息、关系、目标或风险变化。
<!-- chunk:end -->

<!-- chunk:start id="scene_outline.character_tracking" target_format="scene_outline" chunk_type="character_tracking" -->
## scene_outline.character_tracking

**metadata**

- chunk_id: `scene_outline.character_tracking`
- target_format: `scene_outline`
- chunk_type: `character_tracking`
- retrieval_terms: `人物线`, `人物欲望`, `阻力`, `信息差`, `关系变化`, `收益损失`
- prompt_slots: `scene_planning`

**summary**

分场大纲要持续追踪核心人物的欲望、阻力、信息状态、关系变化和行动后果。

**guidance**

每个核心角色至少应有：

- 当前欲望。
- 当前阻力。
- 已知信息和未知信息。
- 与主角的关系变化。
- 本场行动后的收益或损失。

如果角色只提供说明信息，没有欲望和阻力，应考虑合并成道具、线索或已有角色。

**prompt_notes**

可注入提示：每个主要角色都必须有场景内目标；功能重复的说明性角色应合并。

**checks**

- 主要角色是否有欲望和阻力。
- 关系是否随场景变化。
- 信息差是否被追踪。
- 说明性角色是否过多。
<!-- chunk:end -->

<!-- chunk:start id="scene_outline.adaptation_workflow" target_format="scene_outline" chunk_type="adaptation_rule" -->
## scene_outline.adaptation_workflow

**metadata**

- chunk_id: `scene_outline.adaptation_workflow`
- target_format: `scene_outline`
- chunk_type: `adaptation_rule`
- retrieval_terms: `小说转分场`, `事件链`, `因果链`, `秘密`, `反转`, `结构转换`
- prompt_slots: `scene_planning`

**summary**

小说转分场大纲时，应先完成事件链和因果链的结构转换，再决定分场。

**guidance**

建议流程：

1. 抽取原文中的事件链和因果链。
2. 标出不可删除的秘密、反转、人物选择和关系变化。
3. 合并重复功能的事件。
4. 把叙述性段落转为可发生在场景中的行动。
5. 为每场补上目标、阻力和结果。

分场大纲可以保留后续可扩写方向，但不要引入与原文核心矛盾无关的新支线。

**prompt_notes**

可注入提示：先做结构转换，再生成场景；不要按原文段落机械分割。

**checks**

- 是否抽取事件链和因果链。
- 是否保留关键秘密和反转。
- 是否合并重复功能事件。
- 是否避免无关新支线。
<!-- chunk:end -->

<!-- chunk:start id="scene_outline.output_contract" target_format="scene_outline" chunk_type="output_contract" -->
## scene_outline.output_contract

**metadata**

- chunk_id: `scene_outline.output_contract`
- target_format: `scene_outline`
- chunk_type: `output_contract`
- retrieval_terms: `YAML输出`, `scene_outline`, `scene数量`, `beats`, `分场校验`
- prompt_slots: `yaml_writing`, `validation`

**summary**

`scene_outline` 输出应保留目标格式，建议 3 到 8 个 scenes，beats 可更概括，但必须写清戏剧功能、转折和来源章节。

**guidance**

生成 YAML 时：

- `adaptation.target_format` 必须为 `scene_outline`。
- `scenes` 建议 3 到 8 个。
- 每个 scene 的 beats 可以比完整剧本更概括。
- 重点写清楚场景功能、冲突、转折、结果和来源章节。
- 不要生成完整对白稿。

**prompt_notes**

可注入提示：输出必须符合统一 YAML schema，但内容策略按分场大纲执行，强调结构蓝图而非完整剧本。

**checks**

- `adaptation.target_format` 是否等于 `scene_outline`。
- 是否有明确目标、冲突和结果。
- 是否能直接扩写成剧本场景。
- 是否避免完整对白化。
<!-- chunk:end -->
