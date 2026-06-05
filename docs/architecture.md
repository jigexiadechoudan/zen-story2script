# AI 小说转剧本工具架构设计

## 1. 架构目标

本项目是一个“小说转剧本 AI Agent”应用，而不是简单的文本改写工具。系统以 JDK 21 + Spring Boot + Spring AI 为后端基础，采用 ReAct 架构组织智能体能力，让智能体在转换过程中持续执行“思考、行动、观察、修正”，最终输出符合 YAML Schema 的剧本初稿。

这里的 ReAct 指 AI Agent 设计范式中的 Reason + Act，不是前端 React 框架。前端仍使用 Vite + Vue 3 作为比赛演示 UI。

核心原则：

- 后端使用 JDK 21 + Spring Boot + Spring AI，保持评委本地可运行。
- 后端固定为单个 Spring Boot 应用工程，本文中的“模块”指 Java 子包和任务边界；Maven 多模块不作为本项目后端组织方式。
- 核心能力设计为 `NovelToScreenplayAgent`，由它按 ReAct 循环选择和调用工具。
- Spring AI 提供 ChatClient、Advisor、Tool Calling 和结构化输出能力。
- 剧本结果使用 YAML，保证作者可读、可编辑、可下载。
- MVP 首版采用无数据库设计，接口直接返回转换结果；后续可扩展历史记录、版本管理和云端存储。
- 通过 YAML Schema 约束 Agent 输出，降低大模型自由发挥导致的格式风险。

## 2. 系统总览

```text
User
  |
  v
Vue + Vite Frontend
  |  POST /api/convert
  v
Spring Boot + Spring AI Backend
  |
  +-- ConvertController
  +-- NovelToScreenplayAgent
  |     |
  |     +-- ReAct Loop
  |           1. Reason: 分析当前任务状态
  |           2. Act: 选择并调用工具
  |           3. Observe: 读取工具结果
  |           4. Decide: 继续、修复或终止
  |
  +-- Agent Tools
        +-- ChapterParseTool
        +-- StoryAnalysisTool
        +-- ScenePlanningTool
        +-- ScreenplayYamlWriteTool
        +-- YamlSchemaTool
        +-- YamlValidationTool
        +-- YamlRepairTool
  |
  v
Spring AI ChatClient
  |
  v
OpenAI Compatible LLM API
```

## 3. 技术栈

| 层级 | 技术 | 用途 |
| --- | --- | --- |
| Frontend | Vite + Vue 3 | 比赛演示 UI |
| Backend | JDK 21 + Spring Boot | REST API、Agent 编排 |
| Agent Framework | Spring AI | ChatClient、Advisor、Tool Calling、结构化输出 |
| AI Provider | OpenAI compatible Chat Completions API | 大模型推理服务 |
| YAML | SnakeYAML | YAML 解析与基础校验 |
| Test | JUnit 5 + Spring Boot Test | 单元测试和接口测试 |
| Storage | 无数据库 | MVP 接口直接返回结果 |

环境变量：

| 变量名 | 说明 |
| --- | --- |
| `OPENAI_BASE_URL` | OpenAI 兼容接口地址 |
| `OPENAI_API_KEY` | 模型服务 API Key |
| `OPENAI_MODEL` | 使用的模型名称 |

## 4. 推荐目录结构

```text
zen-story2script/
  backend/
    pom.xml
    src/main/java/
      dev/zen/story2script/
        Story2ScriptApplication.java
        api/
        agent/
        tools/
        schema/
        config/
    src/main/resources/
    src/test/java/
  frontend/
    src/
  docs/
    requirements.md
    architecture.md
    mvp-workplan.md
  examples/
    sample-novel.txt
    sample-screenplay.yaml
  README.md
```

后续开发任务拆分以 `docs/mvp-workplan.md` 为准。MVP 后端保持一个 Spring Boot 父工程/单应用，内部按 `api`、`agent`、`tools`、`schema`、`config` 子包分层推进，保证后端 API、Agent 编排、工具实现、YAML 校验和运行时配置可以多线并行。Maven 多模块不作为本项目后端组织方式，以降低比赛交付复杂度。

当前项目中已有 `fronted/` 空目录，后续实现时建议统一使用 `frontend/`，避免 README 和启动命令产生歧义。

## 5. ReAct Agent 设计

### 5.1 `ConvertController`

REST API 入口，负责接收请求、调用智能体、返回结果。

主要职责：

- 校验请求体基础字段。
- 调用 `NovelToScreenplayAgent`。
- 将业务异常转换为可读 HTTP 响应。

### 5.2 `NovelToScreenplayAgent`

小说转剧本核心智能体。它不是固定顺序的流水线，而是根据当前任务状态在每一轮 ReAct 循环中决定下一步要调用哪个工具。

主要职责：

- 接收标题、小说文本、改编目标和风格提示。
- 维护转换上下文，包括章节列表、角色分析、剧情大纲、场景计划、YAML 草稿和校验结果。
- 按 ReAct 循环推进任务：
  - Reason：生成面向系统的决策摘要，判断当前缺少什么信息。
  - Act：选择一个工具执行，例如章节解析、剧情分析、场景规划、YAML 校验。
  - Observe：读取工具输出，更新转换上下文。
  - Decide：判断继续下一轮、触发修复或输出最终结果。
- 控制最大循环次数，避免无限调用。
- 最终返回合法 YAML、质量报告和用户可读警告。

### 5.3 `AgentRuntimeConfig`

Spring AI 运行时配置。

主要职责：

- 创建统一的 `ChatClient`。
- 配置系统 Prompt、模型参数、超时和重试。
- 注册 Agent 可调用的工具。
- 通过 Advisor 记录 Agent 步骤摘要，便于调试和 demo 讲解。
- 不向前端暴露模型隐藏推理链，只返回步骤摘要、质量报告和最终 YAML。

### 5.4 Agent Tools

Agent 工具是 ReAct 架构中的 Act 能力。每个工具只做一类明确任务，便于测试和 PR 拆分。

| 工具 | 职责 |
| --- | --- |
| `ChapterParseTool` | 切分小说章节，校验不少于 3 章 |
| `StoryAnalysisTool` | 抽取章节摘要、关键事件、角色、人物关系和冲突 |
| `ScenePlanningTool` | 将小说事件规划为可拍摄场景和分场大纲 |
| `ScreenplayYamlWriteTool` | 根据上下文生成符合 Schema 的 YAML 剧本草稿 |
| `YamlSchemaTool` | 向 Agent 提供当前 YAML Schema 和字段约束 |
| `YamlValidationTool` | 使用 SnakeYAML 校验 YAML 语法和必填字段 |
| `YamlRepairTool` | 在格式失败时修复 YAML，不重新扩写剧情 |

## 6. ReAct 执行流程

小说转剧本不是简单摘要，而是从叙事文本转为可拍摄、可编辑的剧本结构。行业剧本格式通常强调场景标题、动作、角色、对白、括号提示和转场等元素。系统采用 ReAct 循环，让 Agent 根据观察结果动态推进转换。

```text
Start
  |
  v
Reason: 判断是否已完成章节解析
  |
  v
Act: ChapterParseTool
  |
  v
Observe: 得到章节列表；若少于 3 章则终止
  |
  v
Reason: 判断是否已理解角色、事件和冲突
  |
  v
Act: StoryAnalysisTool
  |
  v
Observe: 得到角色表、章节事件和人物关系
  |
  v
Reason: 判断是否已有可拍摄的场景计划
  |
  v
Act: ScenePlanningTool
  |
  v
Observe: 得到 plot_outline 和 scenes 规划
  |
  v
Reason: 判断是否可以生成 YAML
  |
  v
Act: ScreenplayYamlWriteTool
  |
  v
Observe: 得到 YAML 草稿
  |
  v
Act: YamlValidationTool
  |
  v
Observe: 合法则输出；失败则调用 YamlRepairTool 一次
```

MVP 默认最多执行 8 轮 ReAct 工具调用。若超过上限仍无法得到合法 YAML，则返回错误和最近一次 YAML 草稿，便于用户或开发者排查。

## 7. API 设计

### 7.1 `POST /api/convert`

将小说文本转换为 YAML 剧本。

Request:

```json
{
  "title": "雾镇来信",
  "sourceText": "第一章 ...\n\n第二章 ...\n\n第三章 ...",
  "targetFormat": "short_drama",
  "styleHint": "悬疑、现实主义、节奏紧凑"
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `title` | 是 | 小说或项目标题 |
| `sourceText` | 是 | 3 章以上小说文本 |
| `targetFormat` | 是 | `short_drama`、`screenplay`、`scene_outline` |
| `styleHint` | 否 | 用户希望保留或增强的风格 |

Response:

```json
{
  "yaml": "schema_version: \"1.0\"...",
  "schemaVersion": "1.0",
  "warnings": [
    "检测到 3 个章节，适合生成短剧试读版剧本。"
  ],
  "qualityReport": {
    "chapterCount": 3,
    "characterCount": 4,
    "sceneCount": 8,
    "reactSteps": 6,
    "repaired": false
  },
  "agentTrace": [
    "已完成章节解析",
    "已抽取角色和关键事件",
    "已生成场景计划",
    "YAML 校验通过"
  ]
}
```

`agentTrace` 只返回步骤摘要，不返回模型隐藏推理链。

错误响应示例：

```json
{
  "code": "CHAPTER_COUNT_TOO_LOW",
  "message": "至少需要输入 3 个章节的小说文本。"
}
```

### 7.2 `GET /api/schema`

返回当前 YAML Schema 说明，可用于前端展示或 README 链接。

Response:

```json
{
  "schemaVersion": "1.0",
  "format": "yaml",
  "description": "小说转剧本 YAML Schema"
}
```

### 7.3 `GET /api/health`

用于本地启动检查。

Response:

```json
{
  "status": "ok"
}
```

## 8. YAML Schema 定义

### 8.1 顶层结构

```yaml
schema_version: "1.0"
work:
  title: "雾镇来信"
  original_author: ""
  language: "zh-CN"
  source_chapters:
    count: 3
    range: "第1章-第3章"
adaptation:
  target_format: "short_drama"
  target_duration: "10-15min"
  genre: "悬疑"
  tone: "现实主义、节奏紧凑"
  logline: "一句话故事梗概"
  principles:
    - "保留主线冲突"
    - "压缩旁支情节"
characters:
  - id: "char_001"
    name: "林夏"
    role: "主角"
    identity: "返乡记者"
    personality: "敏感、执着、克制"
    goal: "查清父亲失踪真相"
    arc: "从逃避故乡到主动面对真相"
    relationships:
      - target: "陈默"
        relation: "旧友，互相隐瞒线索"
plot_outline:
  - source_chapter: "第1章"
    key_events:
      - "林夏收到匿名来信后回到雾镇"
    adaptation_choice: "将大段心理描写改为车站动作和电话对白"
scenes:
  - scene_id: "S001"
    scene_type: "EXT"
    location: "雾镇车站"
    time_of_day: "NIGHT"
    characters:
      - "林夏"
      - "陈默"
    summary: "林夏回到雾镇，发现旧友陈默在车站等她。"
    dramatic_purpose: "建立悬疑气氛并引出父亲失踪线索"
    beats:
      - type: "action"
        content: "夜雾压在站台上，林夏拖着行李箱走出候车室。"
      - type: "dialogue"
        speaker: "陈默"
        content: "你不该回来。"
      - type: "parenthetical"
        speaker: "林夏"
        content: "压低声音"
      - type: "dialogue"
        speaker: "林夏"
        content: "信是你寄的？"
      - type: "transition"
        content: "CUT TO:"
notes:
  adaptation_summary: "前三章被压缩为 8 场，保留主线调查和旧友重逢。"
  omitted_elements:
    - "删去与主线无关的校园回忆支线。"
  risks:
    - "部分人物动机仍需作者人工补强。"
  next_steps:
    - "增加第二集结尾反转。"
```

### 8.2 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schema_version` | string | 是 | Schema 版本 |
| `work` | object | 是 | 原小说基础信息 |
| `adaptation` | object | 是 | 改编目标和原则 |
| `characters` | list | 是 | 角色表 |
| `plot_outline` | list | 是 | 小说事件到剧本结构的映射 |
| `scenes` | list | 是 | 剧本场景列表 |
| `notes` | object | 是 | 改编说明和后续建议 |

### 8.3 `beats[].type` 枚举

| 类型 | 含义 |
| --- | --- |
| `action` | 动作、环境、可拍摄画面 |
| `dialogue` | 角色对白 |
| `parenthetical` | 括号提示，如语气、动作状态 |
| `transition` | 转场，如 `CUT TO:`、`FADE OUT:` |

### 8.4 `scene_type` 枚举

| 类型 | 含义 |
| --- | --- |
| `INT` | 内景 |
| `EXT` | 外景 |
| `INT/EXT` | 内外景切换或车内外等复合空间 |

## 9. Schema 设计原因

- 使用 YAML：相比 JSON，YAML 更适合作者阅读和人工编辑，字段含义直观，复制到文档中也更清晰。
- 使用 `schema_version`：后续增加分镜、镜头、集数等字段时，可以保持兼容。
- 拆分 `work` 和 `adaptation`：区分原小说信息与改编目标，避免混淆原作内容和改写策略。
- 独立 `characters[]`：角色信息不依赖单个场景，便于后续做人物一致性检查和角色关系图。
- 保留 `plot_outline[]`：说明每章小说如何被改写为剧本内容，让作者理解 AI 的删改依据。
- 使用 `scenes[]`：剧本天然按场景组织，场景列表方便后续导出为分场表。
- 使用 `beats[]`：场景内部的动作、对白、括号提示和转场必须保持顺序，因此用列表表达，不依赖 YAML mapping 的键顺序。
- 使用 `scene_type`、`location`、`time_of_day`：对应行业剧本中的场景标题信息，便于从文本走向可拍摄剧本。
- 使用 `notes`：保留 AI 的改编总结、删改内容和风险提示，便于作者继续打磨。

## 10. 错误处理

| 场景 | 错误码 | 处理方式 |
| --- | --- | --- |
| 章节少于 3 个 | `CHAPTER_COUNT_TOO_LOW` | 返回错误，不进入 Agent 循环 |
| 请求文本为空 | `EMPTY_SOURCE_TEXT` | 返回错误 |
| AI Key 未配置 | `AI_CONFIG_MISSING` | 返回配置提示 |
| AI 调用超时 | `AI_TIMEOUT` | 返回重试提示 |
| ReAct 超过最大步数 | `AGENT_STEP_LIMIT_EXCEEDED` | 返回最近一次中间结果和错误 |
| AI 输出无法解析 | `YAML_PARSE_FAILED` | 自动修复一次 |
| 修复后仍失败 | `YAML_REPAIR_FAILED` | 返回错误和原始片段 |

## 11. 测试方案

### 11.1 单元测试

- `ChapterParseToolTest`
  - 识别中文数字章节。
  - 识别阿拉伯数字章节。
  - 识别英文 `Chapter 1`。
  - 少于 3 章时抛出错误。
- `YamlValidationToolTest`
  - 合法 YAML 通过校验。
  - 缺少 `scenes` 时失败。
  - `beats[].type` 不在枚举中时失败。
- `NovelToScreenplayAgentTest`
  - 使用 mock 工具验证 Agent 按 ReAct 步骤完成转换。
  - YAML 校验失败时只触发一次修复。
  - 超过最大步数时返回 `AGENT_STEP_LIMIT_EXCEEDED`。

### 11.2 接口测试

- `POST /api/convert` 使用 mock Agent 返回合法 YAML，应返回 `schemaVersion`、`qualityReport` 和 `agentTrace`。
- `POST /api/convert` 输入不足 3 章，应返回 `CHAPTER_COUNT_TOO_LOW`。
- `GET /api/health` 返回 `status=ok`。

### 11.3 手动验收

- 按 README 启动后端和前端。
- 输入 `examples/sample-novel.txt`。
- 页面生成并展示 YAML。
- 页面展示 Agent 步骤摘要。
- 点击复制和下载。
- 对照 `docs/architecture.md` 中的 Schema 检查字段完整性。

## 12. 后续扩展

- 增加 Agent 记忆，将用户偏好的改编风格用于后续作品。
- 增加人工反馈工具，让用户要求 Agent 重写某个场景或角色对白。
- 增加历史记录和版本对比。
- 增加分镜、镜头、集数和人物关系图。
- 增加 Fountain、PDF、Word 等导出格式。
- 增加长篇小说分批处理。
- 增加可视化剧本编辑器，将 YAML 字段转换为可编辑表单。
- 增加模型评估指标，例如角色一致性、场景完整度、对白密度。
