---
doc_id: screenplay_knowledge
title: "影视剧本改编知识"
target_format: "screenplay"
knowledge_type: "adaptation_guidance"
language: "zh-CN"
version: "0.2.0"
last_reviewed: "2026-06-06"
recommended_splitter: "chunk_boundary_comment"
fallback_splitter: "h2"
chunk_boundary_start: "<!-- chunk:start"
chunk_boundary_end: "<!-- chunk:end -->"
source_urls:
  - "https://screenplay.com/pages/basic-screenplay-format"
  - "https://downloads.bbc.co.uk/writersroom/scripts/screenplaytv.pdf"
  - "https://www.finaldraft.com/learn/screenplay-formatting-elements/"
---

# 影视剧本改编知识

## 文档切片契约

本文件面向 RAG ingestion。推荐按 `<!-- chunk:start ... -->` 到 `<!-- chunk:end -->` 切片。每个 chunk 都应作为独立知识单元入库，并继承 front matter 中的 `doc_id`、`target_format`、`language`、`source_urls`。

固定字段：`chunk_id`、`chunk_type`、`retrieval_terms`、`prompt_slots`、`summary`、`guidance`、`checks`。

<!-- chunk:start id="screenplay.intent" target_format="screenplay" chunk_type="intent" -->
## screenplay.intent

**metadata**

- chunk_id: `screenplay.intent`
- target_format: `screenplay`
- chunk_type: `intent`
- retrieval_terms: `影视剧本`, `可拍摄文本`, `视觉化`, `动作`, `对白`, `screenplay`
- prompt_slots: `scene_planning`, `yaml_writing`

**summary**

影视剧本是可拍摄文本，不是小说摘要；应把叙述、心理和背景转为可见动作、可听对白和明确场面。

**guidance**

生成 `screenplay` 时，应让读者能判断：地点在哪里，谁在场，人物想要什么，阻力是什么，场景如何改变故事状态。

小说中的叙述、心理、背景和世界观应改写为可见动作、可听对白、可拍摄场面和明确场景调度。不要只复述故事，也不要把内心独白原样写入。

**prompt_notes**

可注入提示：把抽象信息改写成镜头能捕捉的行动、道具、空间变化、人物选择和对白冲突。

**checks**

- 每场是否可拍摄。
- 是否避免小说摘要式表达。
- 人物目标和阻力是否清楚。
- 场景结束时故事状态是否变化。
<!-- chunk:end -->

<!-- chunk:start id="screenplay.core_elements" target_format="screenplay" chunk_type="format" -->
## screenplay.core_elements

**metadata**

- chunk_id: `screenplay.core_elements`
- target_format: `screenplay`
- chunk_type: `format`
- retrieval_terms: `剧本格式`, `场景标题`, `动作`, `角色名`, `对白`, `括注`, `转场`
- prompt_slots: `yaml_writing`

**summary**

影视剧本的核心元素包括场景标题、动作、角色名、对白、括注和转场；YAML 不必复刻版式，但要保留语义。

**guidance**

标准影视剧本通常包含：

- 场景标题：标明内/外景、具体地点、时间段。
- 动作：描述观众能看到或听到的事件。
- 角色名：标明说话者。
- 对白：角色说出口的话。
- 括注：必要时提示语气、动作或说话对象。
- 转场：必要时标明场景转换方式。

生成 YAML 时不需要完全复刻剧本排版，但应在 `scenes` 和 `beats` 中保留这些剧本元素的语义。

**prompt_notes**

可注入提示：scene metadata 承担场景标题功能，beat type 承担动作、对白、括注和转场功能。

**checks**

- scene 是否有清晰地点和时间。
- beat 是否区分动作和对白。
- 括注是否克制使用。
- 转场是否只在必要时出现。
<!-- chunk:end -->

<!-- chunk:start id="screenplay.scene_heading" target_format="screenplay" chunk_type="format" -->
## screenplay.scene_heading

**metadata**

- chunk_id: `screenplay.scene_heading`
- target_format: `screenplay`
- chunk_type: `format`
- retrieval_terms: `scene heading`, `INT`, `EXT`, `INT/EXT`, `地点`, `时间`
- prompt_slots: `scene_planning`, `yaml_writing`

**summary**

场景标题应简洁、具体、一致，通常由内外景、地点和时间段组成。

**guidance**

建议格式：

- `INT. 书房 - NIGHT`
- `EXT. 河堤 - DAY`
- `INT/EXT. 出租车 - DUSK`

地点应具体，例如先写“病房”，再写“市医院”。时间段不必过细，除非故事信息需要。每个新地点、新时间或连续动作段落通常应拆成新场景。

**prompt_notes**

可注入提示：为每个 scene 生成稳定的 `scene_type`、`location`、`time_of_day`，不要把多个地点混在同一 scene。

**checks**

- `scene_type` 是否是 `INT`、`EXT` 或 `INT/EXT`。
- 地点是否具体。
- 时间是否简洁。
- 是否把明显跨地点的动作拆场。
<!-- chunk:end -->

<!-- chunk:start id="screenplay.action_rules" target_format="screenplay" chunk_type="writing_rule" -->
## screenplay.action_rules

**metadata**

- chunk_id: `screenplay.action_rules`
- target_format: `screenplay`
- chunk_type: `writing_rule`
- retrieval_terms: `动作描写`, `视觉化`, `可拍摄`, `屏幕动作`, `道具`, `反应`
- prompt_slots: `yaml_writing`

**summary**

动作描写只写屏幕上能看到或听到的事；内心变化要外化。

**guidance**

不要写“他想起童年创伤，所以很痛苦”这类不可拍的内心说明。应改为照片、停顿、回避眼神、手部动作、对话选择或环境反应。

动作段落要短。每段最好承担一个清晰 beat：发现、试探、逼近、反击、逃离、沉默、转折。重要道具、声音和首次出现的关键角色可以突出，但不要滥用强调。

**prompt_notes**

可注入提示：所有 action beat 都必须能被镜头捕捉，禁止直接解释角色内心。

**checks**

- action beat 是否可见或可听。
- 是否用动作替代心理解释。
- 每个动作段是否只承担一个 beat。
- 关键道具是否有戏剧功能。
<!-- chunk:end -->

<!-- chunk:start id="screenplay.dialogue_rules" target_format="screenplay" chunk_type="writing_rule" -->
## screenplay.dialogue_rules

**metadata**

- chunk_id: `screenplay.dialogue_rules`
- target_format: `screenplay`
- chunk_type: `writing_rule`
- retrieval_terms: `对白`, `潜台词`, `人物目标`, `信息差`, `冲突对白`
- prompt_slots: `yaml_writing`

**summary**

对白要体现人物目标和潜台词，不应替作者解释剧情。

**guidance**

好的对白通常具备：

- 角色的即时目的。
- 没说出口的真实动机。
- 和对手之间的信息差。
- 推动场景状态变化的结果。

不要让角色解释剧情给观众听；让人物为当下利益说话。括注只在容易误读时使用，且应短。能用动作行表达的情绪，不要塞进括注。

**prompt_notes**

可注入提示：每句 dialogue beat 都要服务人物目标、冲突或信息差；减少说明性对白。

**checks**

- 对白是否有即时目的。
- 是否存在潜台词或信息差。
- 是否推动关系或事件变化。
- 括注是否必要且简短。
<!-- chunk:end -->

<!-- chunk:start id="screenplay.adaptation_rules" target_format="screenplay" chunk_type="adaptation_rule" -->
## screenplay.adaptation_rules

**metadata**

- chunk_id: `screenplay.adaptation_rules`
- target_format: `screenplay`
- chunk_type: `adaptation_rule`
- retrieval_terms: `小说改编`, `内心独白`, `叙述背景`, `人物合并`, `可视化改写`
- prompt_slots: `scene_planning`, `yaml_writing`

**summary**

小说改影视剧本要把叙述性材料转为行动、证据、冲突和关系变化。

**guidance**

优先转换：

- 叙述性背景 -> 可见证据、环境细节或人物行动。
- 内心独白 -> 选择、停顿、回避、冲突对白。
- 长段历史 -> 一场可冲突化的揭示戏。
- 多人物信息 -> 合并为少数有戏剧功能的角色。
- 抽象主题 -> 重复出现的行动、道具、空间或关系变化。

每场戏都应有进入状态和离开状态。如果一场戏结束时人物关系、信息、目标或风险没有变化，这场戏应压缩或删除。

**prompt_notes**

可注入提示：不要保留纯叙述段落；把每段重要原文信息转换成场景内可发生的事件。

**checks**

- 原文心理是否被外化。
- 重复功能角色是否被合并。
- 每场是否有进入和离开状态。
- 抽象主题是否通过行动或道具体现。
<!-- chunk:end -->

<!-- chunk:start id="screenplay.output_contract" target_format="screenplay" chunk_type="output_contract" -->
## screenplay.output_contract

**metadata**

- chunk_id: `screenplay.output_contract`
- target_format: `screenplay`
- chunk_type: `output_contract`
- retrieval_terms: `YAML输出`, `screenplay`, `scene数量`, `beat数量`, `剧本校验`
- prompt_slots: `yaml_writing`, `validation`

**summary**

`screenplay` 输出应保留目标格式，建议 4 到 8 个 scenes，每场具备地点、时间、角色、summary 和 2 到 4 个 beats。

**guidance**

生成 YAML 时：

- `adaptation.target_format` 必须为 `screenplay`。
- `scenes` 建议 4 到 8 个。
- 每个 scene 应包含明确地点、时间、角色和 summary。
- 每个 scene 建议 2 到 4 个 beats。
- beats 应覆盖动作、对白、必要括注或转场。

**prompt_notes**

可注入提示：输出必须符合统一 YAML schema，但内容策略按影视剧本执行，强调可拍摄、可表演、场景状态变化。

**checks**

- `adaptation.target_format` 是否等于 `screenplay`。
- scene 是否有地点、时间和人物。
- beat 是否可拍摄。
- 每场是否改变故事状态。
<!-- chunk:end -->
