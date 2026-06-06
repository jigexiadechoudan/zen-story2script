---
doc_id: short_drama_knowledge
title: "短剧/微短剧改编知识"
target_format: "short_drama"
knowledge_type: "adaptation_guidance"
language: "zh-CN"
version: "0.2.0"
last_reviewed: "2026-06-06"
recommended_splitter: "chunk_boundary_comment"
fallback_splitter: "h2"
chunk_boundary_start: "<!-- chunk:start"
chunk_boundary_end: "<!-- chunk:end -->"
source_urls:
  - "https://screenburn.app/learn/microdrama-structure"
  - "https://www.abff.com/miami/wp-content/uploads/2026/01/2026-ABFF-Microdrama-Storytelling-Guide.pdf"
  - "https://arxiv.org/abs/2602.14045"
  - "https://www.news.cn/politics/20260603/dfd0cce15b6647deac772db0e8fa7f18/c.html"
---

# 短剧/微短剧改编知识

## 文档切片契约

本文件面向 RAG ingestion。推荐按 `<!-- chunk:start ... -->` 到 `<!-- chunk:end -->` 切片。每个 chunk 都应作为独立知识单元入库，并继承 front matter 中的 `doc_id`、`target_format`、`language`、`source_urls`。

每个 chunk 内的固定字段含义：

- `chunk_id`: 稳定切片 ID。
- `chunk_type`: 知识类型，例如 intent、structure、style、guardrail、output_contract。
- `retrieval_terms`: 推荐写入 metadata 或作为召回关键词。
- `prompt_slots`: 推荐注入到哪些生成阶段。
- `summary`: 供 rerank 或摘要展示使用。
- `guidance`: 可直接转成 prompt 的规则。
- `checks`: 生成后可做质量检查的点。

<!-- chunk:start id="short_drama.intent" target_format="short_drama" chunk_type="intent" -->
## short_drama.intent

**metadata**

- chunk_id: `short_drama.intent`
- target_format: `short_drama`
- chunk_type: `intent`
- retrieval_terms: `短剧`, `微短剧`, `竖屏叙事`, `高密度叙事`, `hook`, `cliffhanger`
- prompt_slots: `scene_planning`, `yaml_writing`

**summary**

短剧改编的核心不是压缩长剧，而是为手机端连续观看设计高密度、强钩子、强情绪的戏剧链条。

**guidance**

生成 `short_drama` 时，应优先服务“继续看下一集”的观看动机。开场要迅速建立问题、威胁、羞辱、秘密、身份错位或强欲望；每场内部要持续升级；结尾要留下未解决的反转、危险、误会、关系风险或新证据。

小说改短剧时，不要平均搬运原文情节。先抽取主角欲望、核心阻力、秘密、误会、强情绪关系和可视化证据，再改写为短、直、密的连续戏剧事件。

**prompt_notes**

可注入提示：请把原文中最能制造连续观看冲动的秘密、冲突和身份错位前置；减少背景铺垫，用行动和对抗暴露信息。

**checks**

- 是否在第一场建立明确钩子。
- 是否避免平铺直叙复述小说。
- 是否每场都有升级或反转。
- 是否让最后一场保留继续观看动机。
<!-- chunk:end -->

<!-- chunk:start id="short_drama.episode_structure" target_format="short_drama" chunk_type="structure" -->
## short_drama.episode_structure

**metadata**

- chunk_id: `short_drama.episode_structure`
- target_format: `short_drama`
- chunk_type: `structure`
- retrieval_terms: `单集结构`, `钩子`, `升级`, `悬念`, `三段式`, `短剧节奏`
- prompt_slots: `scene_planning`, `yaml_writing`

**summary**

短剧场景应接近“钩子、升级、悬念”的单集结构，每场只推进一个主要戏剧运动。

**guidance**

单集或单场建议使用三段式：

1. 钩子：开头数秒内抛出冲突、危险、羞辱、秘密、身份错位或强欲望。
2. 升级：围绕一个主要冲突推进，不要同时展开多条支线。
3. 悬念：结尾停在新信息、反转选择、关系破裂、计划失败或更大威胁上。

背景信息应通过冲突、质问、证据、人物选择和可视化物件显露，不要通过旁白式解释堆叠。

**prompt_notes**

可注入提示：每个 scene 的最后一个 beat 尽量承担悬念或转场钩子；不要用长段说明替代戏剧行动。

**checks**

- 每场是否只有一个主要冲突。
- 开头 beat 是否足够快。
- 结尾 beat 是否形成悬念。
- 背景信息是否通过冲突自然暴露。
<!-- chunk:end -->

<!-- chunk:start id="short_drama.series_arc" target_format="short_drama" chunk_type="macro_structure" -->
## short_drama.series_arc

**metadata**

- chunk_id: `short_drama.series_arc`
- target_format: `short_drama`
- chunk_type: `macro_structure`
- retrieval_terms: `短剧弧线`, `连续反转`, `人物反击`, `秘密揭示`, `爽点`
- prompt_slots: `scene_planning`

**summary**

当输入章节较多时，短剧应拆为递进弧线：处境成立、被迫行动、秘密揭开、主动反击。

**guidance**

长篇小说片段可以压缩为四个递进阶段：

1. 身份、处境、核心委屈或强目标迅速成立。
2. 主角被迫行动，阻力升级，出现持续反转。
3. 秘密逐层揭开，关系和利益冲突变得不可逆。
4. 主角主动反击，旧秩序崩塌或付出代价。

即使当前 YAML 只生成 3 到 6 个 scenes，也应让每个 scene 承担一个“短剧集群”的功能：明确起点、情绪转折、结尾钩子。

**prompt_notes**

可注入提示：先规划主角从被动到主动的变化，再安排每场的反转和信息释放。

**checks**

- 主角行动是否从被动逐渐转为主动。
- 秘密是否分层释放，而不是一次性解释完。
- 反转是否改变人物选择或关系。
<!-- chunk:end -->

<!-- chunk:start id="short_drama.dialogue_and_style" target_format="short_drama" chunk_type="style" -->
## short_drama.dialogue_and_style

**metadata**

- chunk_id: `short_drama.dialogue_and_style`
- target_format: `short_drama`
- chunk_type: `style`
- retrieval_terms: `短剧对白`, `短句`, `情绪`, `质问`, `打断`, `证据揭示`
- prompt_slots: `yaml_writing`

**summary**

短剧对白要短句化、情绪化、可表演，动作描写要服务手机端观看。

**guidance**

优先使用质问、反问、打断、沉默后的重击、关键证据揭示。每个角色的台词都应指向当下目标：逼问、隐瞒、诱导、威胁、求救、反击或试探。

动作描写要清晰可视：人物关系、道具、表情、距离变化和关键信息应能被镜头捕捉。不要生成长篇心理描写；内心变化要外化为动作、选择、台词或冲突结果。

**prompt_notes**

可注入提示：对白要短、直接、有冲突；不要让角色用对白解释背景，除非这句解释本身会改变对抗关系。

**checks**

- 台词是否短而有目标。
- 是否存在可表演的动作和反应。
- 是否避免长篇心理说明。
- 每句关键对白是否推动冲突或揭示信息。
<!-- chunk:end -->

<!-- chunk:start id="short_drama.production_constraints" target_format="short_drama" chunk_type="production" -->
## short_drama.production_constraints

**metadata**

- chunk_id: `short_drama.production_constraints`
- target_format: `short_drama`
- chunk_type: `production`
- retrieval_terms: `低成本拍摄`, `小场景`, `少角色`, `道具线索`, `空间复用`
- prompt_slots: `scene_planning`

**summary**

短剧通常更适合少角色、小场景、高复用空间和可反复出现的关键道具。

**guidance**

改编时优先选择：

- 2 到 5 个主要地点。
- 少量核心角色反复碰撞。
- 每场只保留一个核心冲突。
- 关键道具或证据反复出现，形成记忆点。

不要为了还原小说而增加大量地点和群众角色。能合并的人物、地点和事件应合并，保证拍摄可行性和节奏集中。

**prompt_notes**

可注入提示：合并功能重复的人物和地点；优先保留能产生冲突和反转的角色。

**checks**

- 场景数量是否过多。
- 角色是否有重复功能。
- 关键道具或证据是否被有效利用。
- 每场是否具备拍摄可行性。
<!-- chunk:end -->

<!-- chunk:start id="short_drama.compliance_guardrails" target_format="short_drama" chunk_type="guardrail" -->
## short_drama.compliance_guardrails

**metadata**

- chunk_id: `short_drama.compliance_guardrails`
- target_format: `short_drama`
- chunk_type: `guardrail`
- retrieval_terms: `微短剧合规`, `低俗`, `软色情`, `拜金`, `暴力复仇`, `侵权盗版`
- prompt_slots: `yaml_writing`, `post_generation_review`

**summary**

中文微短剧生成应保留强冲突，但避免把低俗、擦边、极端复仇、侵权等风险元素作为卖点。

**guidance**

可以写冲突和反派，但不要美化违法伤害、极端报复、歧视性叙事、软色情擦边、拜金炫富、畸形婚恋观、封建糟粕、涉儿童有害内容、侵权盗版等元素。

爽点应落在人物成长、正当反击、证据闭合、关系修复或代价承担上。

**prompt_notes**

可注入提示：保留强冲突，但不要把违规或低俗元素写成吸引点；让主角行动承担合理后果。

**checks**

- 是否存在低俗或擦边卖点。
- 是否美化违法伤害或极端报复。
- 是否出现侵权盗版相关设定。
- 是否让冲突结果具有正当性或代价。
<!-- chunk:end -->

<!-- chunk:start id="short_drama.output_contract" target_format="short_drama" chunk_type="output_contract" -->
## short_drama.output_contract

**metadata**

- chunk_id: `short_drama.output_contract`
- target_format: `short_drama`
- chunk_type: `output_contract`
- retrieval_terms: `YAML输出`, `scene数量`, `beats数量`, `target_format`, `短剧校验`
- prompt_slots: `yaml_writing`, `validation`

**summary**

`short_drama` 输出应保留目标格式，建议 3 到 6 个 scenes，每个 scene 2 到 4 个 beats，最后 beat 优先承担钩子。

**guidance**

生成 YAML 时：

- `adaptation.target_format` 必须为 `short_drama`。
- `scenes` 建议 3 到 6 个。
- 每个 scene 的 `beats` 建议 2 到 4 个。
- 每个 scene 的最后一个 beat 尽量承担悬念、反转或转场钩子。
- `notes` 可记录删减、合并或合规处理说明。

**prompt_notes**

可注入提示：输出必须符合统一 YAML schema，但内容策略按短剧执行，优先压缩、强化冲突和保留悬念。

**checks**

- `adaptation.target_format` 是否等于 `short_drama`。
- scene 数量是否在建议范围内。
- beat 是否短而有效。
- 最后 scene 是否留下强记忆点或阶段性钩子。
<!-- chunk:end -->
