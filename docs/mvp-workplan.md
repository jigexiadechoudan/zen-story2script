# MVP 工作计划

本文用于说明 Zen Story2Script 当前 MVP 的模块边界、交付状态和后续扩展方向。这里的“模块”指工作分工和 Java package 边界，不代表 Maven 多模块。

## MVP 目标

完成一条从“至少 3 章小说输入”到“结构化剧本 YAML 草稿”的完整演示链路：

- 用户可以注册、登录和登出。
- 前端可以输入小说标题、正文、改编目标、转换模式和风格提示。
- 后端通过 Agent 执行章节解析、故事分析、场景规划、YAML 生成、校验和必要修复。
- 前端通过 SSE 展示实时进度。
- 用户可以查看、复制、下载 YAML。
- README、docs 和 examples 能支撑本地复现。

## 模块边界

| 模块 | 当前职责 | 状态 |
| --- | --- | --- |
| 项目基础 | 后端、前端、docs、examples 目录和启动约定 | 已具备 |
| 后端 API | health、schema、auth、convert、convert stream | 已具备 |
| Auth | 注册、登录、Cookie token、受保护转换接口 | 已具备 |
| ReAct Agent | `NovelToScreenplayAgent` 编排转换步骤 | 已具备 |
| Agent Tools | 章节解析、故事分析、场景规划、YAML 写作、校验、修复 | 已具备 |
| YAML Schema | v1.0 结构校验和文档 | 已具备 |
| RAG 知识层 | 内置知识、本地检索、可选 PGVector | 已具备 |
| 前端工作台 | 登录注册、输入、模式选择、SSE、YAML、质量报告 | 已具备 |
| 示例与文档 | 原创示例文本、示例 YAML、启动说明 | 本次补齐 |

## 推荐 PR 拆分

适合后续继续开发的 PR 粒度：

1. `docs: update demo and schema documentation`
2. `feat(backend): improve yaml validation diagnostics`
3. `feat(backend): persist conversion history`
4. `feat(frontend): add screenplay outline editor`
5. `feat(export): add fountain export`
6. `test: cover stream conversion flow`

每个 PR 应包含：

- 功能描述。
- 实现思路。
- 验证方式。
- 是否新增依赖。
- 是否涉及敏感配置或用户内容。

## 当前约束

- 后端保持单个 Spring Boot 工程。
- 前端保持 Vue 3 + Vite + JavaScript。
- 不引入大型 UI 组件体系。
- API 保持原生 fetch 调用。
- YAML 是 MVP 核心交付物，不隐藏原文。
- Agent trace 返回步骤摘要，不暴露模型隐藏推理链。
- dev fallback 和 local mock fallback 必须保留。

## 后续扩展路线

### 作品管理

- 保存小说输入、YAML 输出和转换参数。
- 支持历史版本对比。
- 支持用户删除作品和导出全部数据。

### 编辑体验

- 将 YAML 渲染为可编辑分场表。
- 支持单场重写、对白重写和人物关系修改。
- 支持 YAML 与可视化表单双向同步。

### 导出能力

- Markdown 剧本导出。
- Fountain 导出。
- Word / PDF 导出。
- 分场表和拍摄清单导出。

### 长文本处理

- 长篇小说分批处理。
- 跨章节角色一致性检查。
- 大纲层、分集层、分场层分阶段生成。

### 质量评估

- 角色目标清晰度。
- 场景完整度。
- 对白密度。
- 冲突强度。
- YAML 结构稳定性。

## 提交前检查清单

- README 启动命令可执行。
- `examples/` 只包含原创、授权或公共领域材料。
- 文档中没有真实 API Key、token、数据库密码、真实邀请码或私有代理地址。
- 不提交 `application-local.yml`。
- 前端构建通过：`npm run build`。
- 后端测试通过：`mvn test`。
