---
doc_id: rag_corpus_index
title: "Story2Script adaptation knowledge corpus"
target_format: "all"
language: "zh-CN"
version: "0.2.0"
last_reviewed: "2026-06-06"
recommended_splitter: "chunk_boundary_comment"
fallback_splitter: "h2"
chunk_boundary_start: "<!-- chunk:start"
chunk_boundary_end: "<!-- chunk:end -->"
---

# Story2Script 改编目标知识库索引

本目录存放 RAG 静态知识资源。当前覆盖三种 `targetFormat`：

| targetFormat | 文档 | 主要用途 |
| --- | --- | --- |
| `short_drama` | `short_drama_knowledge.md` | 短剧/微短剧改编：钩子、升级、悬念、短句对白、低成本场景、合规边界。 |
| `screenplay` | `screenplay_knowledge.md` | 影视剧本改编：可拍摄动作、剧本元素、场景标题、对白潜台词、小说视觉化改写。 |
| `scene_outline` | `scene_outline_knowledge.md` | 分场大纲改编：逐场拆解、场景目标、冲突结果、人物线追踪、因果链。 |

## 推荐切片协议

优先按 HTML 注释边界切片：

```text
<!-- chunk:start id="..." target_format="..." chunk_type="..." -->
...
<!-- chunk:end -->
```

切片时建议：

1. 读取文件级 YAML front matter，作为所有 chunk 的基础 metadata。
2. 用 `chunk:start` 注释解析 chunk 级 metadata：`id`、`target_format`、`chunk_type`。
3. 将 chunk 正文中的 `**metadata**`、`**summary**`、`**guidance**`、`**prompt_notes**`、`**checks**` 分段保留。
4. 入库文本建议包含 `summary + guidance + prompt_notes + checks`，metadata 字段单独入库。
5. 如果切片器不支持注释边界，可退化为按二级标题 `## {target}.{topic}` 切分，但要跳过“文档切片契约”章节。

## 建议 metadata

每个 chunk 入库建议至少包含：

- `doc_id`
- `title`
- `target_format`
- `knowledge_type`
- `language`
- `version`
- `last_reviewed`
- `source_urls`
- `chunk_id`
- `chunk_type`
- `retrieval_terms`
- `prompt_slots`

## 检索策略

1. 先用请求里的 `targetFormat` 做 metadata filter。
2. 再用 `styleHint`、小说摘要、当前 Agent 步骤拼接成语义 query。
3. `ScenePlanningTool` 优先召回 `intent`、`structure`、`macro_structure`、`granularity`、`dramatic_function`、`adaptation_rule` 类型。
4. `ScreenplayYamlWriteTool` 优先召回 `style`、`writing_rule`、`format`、`output_contract`、`guardrail` 类型。
5. Prompt 注入时每次控制在 2 到 4 个 chunk，避免把静态知识压过用户原文。

## Prompt 注入建议

可以先不上向量库，按 `targetFormat` 精确读取对应文档的核心 chunk：

- 短剧：`short_drama.intent`、`short_drama.episode_structure`、`short_drama.output_contract`。
- 影视剧本：`screenplay.intent`、`screenplay.action_rules`、`screenplay.output_contract`。
- 分场大纲：`scene_outline.intent`、`scene_outline.scene_unit`、`scene_outline.output_contract`。

后续接入向量库后，再用 metadata filter + semantic search 替换静态读取。
