# MVP 多模块推进工作文档

## 1. 文档目标

本文档用于后续 AI 或多人并行开发时拆分任务。这里的“模块”指工作分工和 Java 子包边界，不是 Maven 多模块。当前阶段只交付最小 MVP：完成一次从“3 章以上小说输入”到“小说转剧本 AI Agent 生成 YAML 剧本初稿”的完整演示链路。

MVP 的核心结果是：

- 前端可以输入小说标题、正文、改编目标和风格提示。
- 后端通过 `NovelToScreenplayAgent` 按 ReAct 架构完成转换。
- 系统返回合法 YAML、Agent 步骤摘要和质量报告。
- 用户可以复制和下载 YAML。
- README 能让评委本地复现。

MVP 首版暂缓、后续可扩展的能力：登录、数据库、多人协作、长期记忆、专业格式导出、长篇全集处理。

工程约束：

- 后端是一个 JDK 21 + Spring Boot + Spring AI 单应用工程。
- `backend/` 下只保留一个 `pom.xml`，后端固定为单个 Spring Boot 应用工程。
- 后端内部按 Java 子包拆分：`api`、`agent`、`tools`、`schema`、`config`。
- 多线推进通过 PR 和包边界完成，Maven module 不作为本项目后端组织方式。

## 2. 模块总览

| 模块 | 目标 | 可并行性 |
| --- | --- | --- |
| 项目基础 | 建立目录、README、基础工程和启动约定 | 可最先做 |
| 后端 API | 提供 health、schema、convert 接口契约 | 可与前端并行 |
| ReAct Agent | 实现 `NovelToScreenplayAgent` 编排逻辑 | 依赖 API DTO 和工具接口 |
| Agent Tools | 实现章节解析、故事分析、场景规划、YAML 写作、修复工具 | 可分工具并行 |
| YAML Schema/校验 | 固化 Schema，完成 YAML 解析和字段校验 | 可独立并行 |
| 前端演示 UI | 提供单页转换工作台 | 可先接 mock API |
| 示例与 README | 提供示例输入输出和复现说明 | 可独立并行 |
| 测试与 demo | 覆盖关键链路并准备视频脚本 | 后置收口 |

## 3. 项目基础模块

### 目标

让仓库具备清晰的 MVP 工程结构，后续不同 AI 或开发者可以按工作模块和 Java 子包推进。

### 输入

- 当前空目录 `backend/`、`fronted/`。
- 已有文档 `docs/requirements.md`、`docs/architecture.md`。

### 输出

- 标准目录结构。
- README 初稿。
- 前后端启动约定。

### 必须完成任务

- 初始化 Git 仓库，并确认仓库创建时间和 commit 时间符合比赛批次要求。
- 将 `fronted/` 统一为 `frontend/`，后续 README 和脚本只使用 `frontend/`。
- 创建 `backend/` Spring Boot 单应用工程，使用 JDK 21、Spring Boot、Spring AI。
- `backend/` 只放一个 `pom.xml`，保持单个 Spring Boot 应用工程。
- 在统一应用包下创建 `api`、`agent`、`tools`、`schema`、`config` 子包。
- 创建 `frontend/` Vite + Vue 3 工程。
- 创建 `examples/`，用于存放示例小说和示例 YAML。
- README 写明仓库地址、技术栈、启动方式、AI 配置、文档入口、第三方依赖和 demo 视频占位。

### 首版边界与后续扩展

- 数据库首版暂缓，后续可扩展作品保存、转换历史和版本对比。
- 登录注册首版暂缓，后续可扩展作者账号、团队空间和个人配置。
- 部署脚本首版暂缓，后续可扩展 Docker Compose、云端部署和一键演示环境。

### 验收标准

- 根目录结构清晰，至少包含 `backend/`、`frontend/`、`docs/`、`examples/`、`README.md`。
- README 能说明项目用途和本地启动方式。
- 后续 PR 可以按 Java 子包和任务边界独立推进。

### 建议 PR

`chore: initialize project structure`

## 4. 后端 API 模块

### 目标

先建立稳定接口契约，允许前端和 Agent 模块并行开发。

### 输入

- `docs/architecture.md` 中定义的 API。
- mock Agent 返回值。

### 输出

- `GET /api/health`
- `GET /api/schema`
- `POST /api/convert`

### 必须完成任务

- 定义 `ConvertRequest`：
  - `title`
  - `sourceText`
  - `targetFormat`
  - `styleHint`
- 定义 `ConvertResponse`：
  - `yaml`
  - `schemaVersion`
  - `warnings`
  - `qualityReport`
  - `agentTrace`
- 定义统一错误响应：
  - `code`
  - `message`
- `POST /api/convert` 初期可以调用 mock `NovelToScreenplayAgent`，保证接口先可用。
- `GET /api/schema` 返回 Schema 版本和说明。
- `GET /api/health` 返回 `status=ok`。

### 首版边界与后续扩展

- Controller 首版仅负责接口适配；后续即使扩展鉴权、限流或审计，也保持 Agent 逻辑在 `agent` 子包。
- API 模块首版不直接调用模型；后续扩展异步任务、队列或缓存时，仍通过 Agent 服务进入模型能力。

### 验收标准

- 三个接口可通过本地请求验证。
- 输入不足 3 章时预留 `CHAPTER_COUNT_TOO_LOW` 错误码。
- 前端可以基于接口契约开发。

### 建议 PR

`feat(backend): add api contracts`

## 5. ReAct Agent 模块

### 目标

实现小说转剧本的核心智能体 `NovelToScreenplayAgent`，通过 ReAct 循环选择工具并产出最终 YAML。

### 输入

- `ConvertRequest`
- Agent Tools 接口
- YAML 校验结果

### 输出

- 合法 YAML
- `qualityReport`
- `agentTrace`
- 业务错误

### 必须完成任务

- 定义 `AgentContext`，保存：
  - 标题、原文、改编目标、风格提示。
  - 章节列表。
  - 故事分析结果。
  - 场景规划结果。
  - YAML 草稿。
  - YAML 校验结果。
  - Agent 步骤摘要。
- 实现最多 8 轮 ReAct 调用控制。
- 每轮执行：
  - Reason：基于上下文判断下一步。
  - Act：调用一个工具。
  - Observe：记录工具结果。
  - Decide：判断继续、修复或结束。
- 只向前端返回 `agentTrace` 步骤摘要，不暴露模型隐藏推理链。
- YAML 校验失败时最多调用一次 `YamlRepairTool`。

### 首版边界与后续扩展

- 长期记忆首版暂缓，后续可扩展用户偏好、作品风格记忆和角色设定记忆。
- 跨用户上下文首版暂缓，后续可在账号体系建立后扩展团队空间。
- 无限重试不作为产品行为；后续可扩展有上限的重试策略、人工接管和失败恢复。

### 验收标准

- mock 工具场景下，Agent 可以按章节解析、故事分析、场景规划、YAML 写作、校验的顺序完成一次转换。
- 超过最大步数返回 `AGENT_STEP_LIMIT_EXCEEDED`。
- 修复工具最多执行一次。

### 建议 PR

`feat(backend): wire novel to screenplay agent`

## 6. Agent Tools 模块

### 目标

将 ReAct 中的 Act 能力拆成小工具，每个工具只负责一个明确任务。

### 工具拆分

| 工具 | 输入 | 输出 | MVP 任务 |
| --- | --- | --- | --- |
| `ChapterParseTool` | 小说原文 | 章节列表 | 识别章节并校验不少于 3 章 |
| `StoryAnalysisTool` | 章节列表 | 角色、事件、冲突 | 使用 Spring AI 提炼故事结构 |
| `ScenePlanningTool` | 故事分析结果 | 分场计划 | 生成可拍摄场景和 `plot_outline` |
| `ScreenplayYamlWriteTool` | 上下文与 Schema | YAML 草稿 | 生成纯 YAML |
| `YamlSchemaTool` | 无或版本号 | Schema 文本 | 提供字段约束 |
| `YamlRepairTool` | YAML 草稿和错误 | 修复后 YAML | 只修格式和缺失字段 |

### 必须完成任务

- 每个工具都有清晰输入输出对象。
- LLM 工具通过 Spring AI ChatClient 调用模型。
- Prompt 明确要求只返回目标结构，不返回 Markdown 代码块。
- 工具失败时返回可读错误，供 Agent 决策。

### 首版边界与后续扩展

- 单工具首版保持单一职责；后续可扩展复合工具，但仍需保持输入输出清晰。
- 工具首版不控制整个 Agent 流程；后续可扩展工具编排能力，但主流程仍由 `NovelToScreenplayAgent` 管理。

### 验收标准

- `ChapterParseTool` 可单元测试。
- LLM 工具可通过 mock ChatClient 测试。
- 工具输出可被 Agent 上下文保存。

### 建议 PR

`feat(backend): add react agent tools`

## 7. YAML Schema/校验模块

### 目标

确保 Agent 输出是可解析、可编辑、字段完整的 YAML 剧本。

### 输入

- YAML 字符串。
- `docs/architecture.md` 中定义的 Schema。

### 输出

- 校验通过结果。
- 校验失败原因列表。

### 必须完成任务

- 固化 Schema 版本 `1.0`。
- 使用 SnakeYAML 解析 YAML。
- 校验顶层字段：
  - `schema_version`
  - `work`
  - `adaptation`
  - `characters`
  - `plot_outline`
  - `scenes`
  - `notes`
- 校验列表字段：
  - `characters` 必须是列表。
  - `plot_outline` 必须是列表。
  - `scenes` 必须是列表。
  - `scenes[].beats` 必须是列表。
- 校验枚举：
  - `beats[].type` 只允许 `action`、`dialogue`、`parenthetical`、`transition`。
  - `scene_type` 只允许 `INT`、`EXT`、`INT/EXT`。
- 输出错误消息，供 `YamlRepairTool` 使用。

### 首版边界与后续扩展

- 完整 JSON Schema 引擎首版暂缓，后续可扩展为独立 Schema 文件、版本迁移和更严格校验。
- 剧本文学质量评分首版暂缓，后续可扩展角色一致性、对白密度、场景完整度等质量指标。

### 验收标准

- 合法示例 YAML 校验通过。
- 缺少顶层字段时校验失败。
- 非法 `beats[].type` 校验失败。

### 建议 PR

`feat(backend): add yaml schema validation`

## 8. 前端演示 UI 模块

### 目标

做一个直接进入核心功能的单页工作台，满足比赛 demo 展示。

### 输入

- 用户填写的标题、小说正文、改编目标、风格提示。
- 后端 `/api/convert` 响应。

### 输出

- YAML 预览。
- Agent 步骤摘要。
- 质量报告。
- 错误提示。

### 必须完成任务

- 页面首屏就是转换工作台，不做营销页。
- 左侧输入：
  - 标题输入框。
  - 小说文本输入框。
  - 改编目标选择。
  - 风格提示输入。
  - 转换按钮。
- 右侧展示：
  - YAML 文本。
  - Agent 步骤摘要。
  - 质量报告。
  - 复制按钮。
  - 下载按钮。
- loading 状态禁止重复提交。
- 错误状态显示后端 `message`。
- 前端可先使用 mock 响应开发，后续切到真实 API。

### 首版边界与后续扩展

- 登录入口首版暂缓，后续可在账号体系上线后加入。
- 多页面官网首版暂缓，后续可扩展作品介绍、案例展示和帮助中心。
- 复杂编辑器首版暂缓，后续可扩展可视化分场编辑、角色表编辑和对白重写。

### 验收标准

- 输入示例小说后可以触发转换。
- 成功后能复制和下载 YAML。
- 后端错误能清晰显示。
- 页面在常见桌面宽度下无明显布局重叠。

### 建议 PR

`feat(frontend): add conversion workspace`

## 9. 示例与 README 模块

### 目标

让评委无需理解源码，也能快速启动、验证和理解作品。

### 必须完成任务

- 新增 `examples/sample-novel.txt`，包含至少 3 章示例小说。
- 新增 `examples/sample-screenplay.yaml`，展示预期输出。
- README 包含：
  - 项目简介。
  - 技术栈。
  - 后端启动方式。
  - 前端启动方式。
  - 环境变量配置。
  - 第三方依赖说明。
  - 原创功能说明。
  - 知识产权提示：用户需确认输入文本具有使用权，示例文本为原创。
  - 学术诚信说明：项目自主完成，复用历史代码时在 PR 中注明来源。
  - demo 视频链接占位。
  - 文档入口：需求、架构、MVP 工作计划。
- README 说明无 API Key 时可查看示例输出。

### 首版边界与后续扩展

- 启动命令首版必须可执行；后续新增部署方式时同步更新 README。
- 第三方依赖首版必须列明；后续新增依赖时同步补充用途和原创功能边界。
- 示例文本首版使用原创短篇片段；后续如支持用户上传或公开分享，需要补充授权确认和内容删除能力。

### 验收标准

- README 可指导本地复现。
- 示例小说不少于 3 章。
- 示例 YAML 符合 Schema。
- README 列明第三方依赖、依赖用途、原创功能部分和知识产权提示。

### 建议 PR

`docs: add examples and demo guide`

## 10. 测试与 demo 模块

### 目标

保证 MVP 核心链路可验证，demo 视频可以完整讲清楚。

### 必须完成任务

- 后端单元测试：
  - 章节解析。
  - YAML 校验。
  - Agent mock 流程。
- 后端接口测试：
  - `GET /api/health`
  - `GET /api/schema`
  - `POST /api/convert`
- 前端手动验收：
  - 输入。
  - 转换。
  - loading。
  - 错误提示。
  - YAML 复制和下载。
- demo 视频脚本覆盖：
  - 题目目标。
  - ReAct Agent 工作方式。
  - 3 章小说输入。
  - YAML 输出。
  - Schema 设计价值。

### 验收标准

- 后端测试通过。
- README 流程可跑通。
- demo 视频能展示完整核心链路。

### 建议 PR

`test: cover mvp conversion flow`

## 11. 并行推进策略

### 可并行启动

- A 线：后端 API + DTO。
- B 线：YAML Schema/校验。
- C 线：前端演示 UI。
- D 线：README + examples。

### 依赖关系

- ReAct Agent 依赖后端 API DTO、Agent Tools、YAML 校验。
- Agent Tools 中的 `ChapterParseTool` 和 `YamlSchemaTool` 可先做，LLM 工具可后接 Spring AI。
- 前端可先用 mock `/api/convert` 响应开发。
- README 可先写启动占位，等实际工程初始化后补齐命令。

### 推荐 PR 顺序

1. `docs: add mvp workplan`
2. `chore: initialize project structure`
3. `feat(backend): add api contracts`
4. `feat(backend): add yaml schema validation`
5. `feat(backend): add react agent tools`
6. `feat(backend): wire novel to screenplay agent`
7. `feat(frontend): add conversion workspace`
8. `docs: add examples and demo guide`
9. `test: cover mvp conversion flow`

### PR 描述模板

每个 PR 必须包含：

```text
标题：一句话说明本 PR 新增/修改了什么

功能描述：
- 说明该功能的作用与使用方式。

实现思路：
- 简要说明技术选型或核心实现逻辑。
- 如引用第三方库、模板或复用历史代码，在这里注明来源。

测试方式：
- 说明如何验证功能正常运行。
- 写明执行过的命令、接口请求或手动验收步骤。

原创与依赖说明：
- 本 PR 原创实现的部分。
- 新增第三方依赖及其用途。
```

PR 合并后主分支必须保持可运行。所有 commit 时间戳应落在所选批次开始与截止时间内，避免最后一天一次性导入全部代码。

## 12. 交付检查清单

- `docs/mvp-workplan.md` 已存在。
- MVP 范围明确，不混入长期功能。
- 每个模块都有目标、任务、首版边界与后续扩展、验收标准和建议 PR。
- 并行推进关系清楚。
- PR 描述模板、依赖披露、原创说明、知识产权提示和 commit 时间要求已覆盖。
- 文档与 `NovelToScreenplayAgent`、ReAct、JDK 21、Spring Boot、Spring AI 保持一致。
