# 架构设计

Zen Story2Script 是一个“小说转结构化剧本 YAML”的 AI Agent Web 应用。系统不是简单文本改写器，而是把小说输入拆成章节、人物、事件、场景和剧本节拍，再输出可校验、可编辑、可复制、可下载的 YAML 草稿。

本文描述当前仓库真实架构。

## 总览

```text
User
  |
  v
Vue 3 + Vite Frontend
  |  GET/POST /api/auth/*
  |  POST /api/convert
  |  POST /api/convert/stream
  v
Spring Boot Backend
  |
  +-- AuthController / Spring Security
  +-- ConvertController
  +-- AgentNovelToScreenplayService
  +-- NovelToScreenplayAgent
  |     |
  |     +-- AgentPlanner
  |     +-- ChapterParseTool
  |     +-- StoryAnalysisTool
  |     +-- ScenePlanningTool
  |     +-- ScreenplayYamlWriteTool
  |     +-- YamlValidationTool
  |     +-- YamlRepairTool
  |
  +-- YamlSchemaValidator
  +-- RAG Knowledge Retriever
  +-- H2 / PostgreSQL
  |
  v
Spring AI ChatClient / OpenAI-compatible API
```

## 后端分层

后端是单个 Spring Boot 应用，不使用 Maven 多模块。内部通过 Java package 分层：

| 包 | 职责 |
| --- | --- |
| `api.controller` | HTTP 入口：健康检查、Schema、转换 |
| `api.dto` | 请求、响应和 SSE 事件 DTO |
| `api.error` | API 异常和统一错误响应 |
| `api.service` | Controller 与 Agent 之间的应用服务 |
| `auth` | 注册、登录、Cookie token、用户实体与认证 DTO |
| `agent` | ReAct 风格 Agent 状态、规划、执行结果和主编排 |
| `tools` | 章节解析、故事分析、场景规划、YAML 写作、校验、修复 |
| `schema` | YAML Schema v1.0 常量和校验器 |
| `rag` | 内置改编知识加载、本地检索和可选 PGVector 检索 |
| `config` | Spring AI、工具、CORS、安全和 dev fallback 配置 |

## 前端架构

前端是 Vue 3 + Vite 单页应用，不使用 Vue Router、Pinia、Tailwind 或 TypeScript。业务状态集中在 `App.vue`，展示拆成组件：

| 文件 | 职责 |
| --- | --- |
| `frontend/src/api.js` | 原生 fetch、认证 API、转换 API、SSE 读取、本地 mock fallback |
| `frontend/src/App.vue` | 顶层状态、locale、认证、转换、复制下载 |
| `frontend/src/components/AppHeader.vue` | 顶部产品、用户、语言和状态 |
| `frontend/src/components/AuthPanel.vue` | 登录注册入口 |
| `frontend/src/components/ConversionForm.vue` | 小说输入与目标选择 |
| `frontend/src/components/ModeSelector.vue` | 转换模式选择 |
| `frontend/src/components/ProgressTimeline.vue` | Agent 步骤展示 |
| `frontend/src/components/YamlPreview.vue` | YAML 预览、复制、下载 |
| `frontend/src/components/QualityPanel.vue` | 质量报告 |
| `frontend/src/components/WarningList.vue` | warnings 展示 |

前端保留中英文切换能力，中文体验优先。API 调用保持原生 `fetch`，认证使用 `credentials: 'include'` 携带 HTTP-only Cookie。

## 认证边界

认证由 Spring Security 保护：

- 公开：`/api/health`、`/api/schema`、`/api/auth/register`、`/api/auth/login`
- 需登录：`/api/auth/me`、`/api/auth/logout`、`/api/convert`、`/api/convert/stream`

注册字段：

```json
{
  "email": "judge@example.local",
  "password": "local-demo-password",
  "displayName": "评审演示",
  "inviteCode": "dev-invite"
}
```

登录成功后，后端签发 token 并写入 HTTP-only Cookie。密码只保存 BCrypt hash，不保存明文。

## 转换流程

同步接口和流式接口都使用同一个转换服务：

1. 前端校验标题、正文长度和至少 3 章。
2. 后端校验请求字段非空。
3. Agent 读取 `conversionMode`，缺省为 `fast`。
4. `fast` 用较短链路生成草稿。
5. `react` 通过 Agent 步骤推进章节解析、故事分析、场景规划、YAML 生成、校验和修复。
6. YAML 校验通过后返回 `ConvertResponse`。
7. 前端展示 YAML、warnings、qualityReport 和 agentTrace。

SSE 事件结构：

```json
{
  "type": "step",
  "message": "chapter_parse: 已识别并校验小说章节。",
  "data": null
}
```

最终事件：

```json
{
  "type": "result",
  "message": "",
  "data": {
    "yaml": "schema_version: \"1.0\"\n...",
    "schemaVersion": "1.0",
    "warnings": [],
    "qualityReport": {},
    "agentTrace": {
      "mode": "react",
      "steps": []
    }
  }
}
```

## YAML 校验

`YamlSchemaValidator` 使用 SnakeYAML `SafeConstructor` 解析 YAML，并检查：

- 根节点必须是对象。
- 顶层字段必须固定。
- `schema_version` 必须为 `"1.0"`。
- `characters`、`plot_outline`、`scenes` 必须为列表。
- `scene_type` 只允许 `INT`、`EXT`、`INT/EXT`。
- `beats[].type` 只允许 `action`、`dialogue`、`parenthetical`、`transition`。

完整字段说明见 [screenplay-yaml-schema.md](screenplay-yaml-schema.md)。

## RAG 知识层

`rag/staticresources/` 包含内置改编知识，按目标格式区分：

- `short_drama_knowledge.md`
- `screenplay_knowledge.md`
- `scene_outline_knowledge.md`

默认可使用内存检索。配置 PGVector 和 embedding 后，可以切换到向量检索：

- `STORY2SCRIPT_RAG_VECTOR_STORE_ENABLED=true`
- `STORY2SCRIPT_RAG_SYNC_ON_STARTUP=true`
- `SPRING_AI_VECTORSTORE_TYPE=pgvector`

RAG 只用于增强提示，不改变用户输入授权边界。

## 运行模式

| 模式 | 用途 | 数据库 | 模型 |
| --- | --- | --- | --- |
| `dev` | 无密钥本地演示 | H2 内存库 | 关闭真实模型，使用 fallback |
| `local` | 真实模型本地联调 | PostgreSQL 或自定义 | OpenAI-compatible |
| `prod` | 后续部署扩展 | PostgreSQL | OpenAI-compatible |

## 关键设计取舍

- 保持单体 Spring Boot：降低 MVP 交付复杂度。
- 前端不引入大型状态和路由体系：当前只有一个核心工作台。
- YAML 作为核心交付物：可读、可编辑、可校验、可导出。
- Agent trace 只返回步骤摘要：不暴露模型隐藏推理链。
- dev fallback 保留：无密钥也能演示认证、SSE、YAML 预览和下载链路。
- 示例文本使用原创内容：避免版权和隐私风险。
