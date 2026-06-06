# Zen Story2Script

Zen Story2Script 是一个“小说文本转结构化剧本 YAML”的 AI Agent MVP。项目当前重点是后端转换链路：接收原创小说文本，按章节解析、故事分析、场景规划、剧本 YAML 生成和 YAML 修复/校验步骤，输出便于前端展示、人工编辑或后续导出的结构化剧本草稿。

## 技术栈

- 后端：Java 21、Spring Boot 3.5、Spring Web、Spring Validation
- AI 接入：Spring AI 1.0、OpenAI Chat 模型适配
- YAML：SnakeYAML，用于剧本 YAML 解析和 Schema 校验
- 构建与测试：Maven、JUnit 5、AssertJ
- 前端：仓库保留 `fronted/` 目录，但当前没有可执行的前端工程或 `package.json`

## 本地复现

### 前置要求

- JDK 21
- Maven 3.9+ 或兼容版本
- 可选：OpenAI API Key。默认 `dev` profile 不调用真实模型，可直接启动后端并使用内置 fallback 行为联调。

### 后端启动方式

```powershell
cd backend
mvn spring-boot:run
```

默认端口为 `8080`。启动后可检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

获取 API 输入/输出契约摘要：

```powershell
Invoke-RestMethod http://localhost:8080/api/schema
```

使用示例小说调用转换接口：

```powershell
$novel = Get-Content ..\examples\sample-novel.txt -Raw
$body = @{
  title = "雨夜校准"
  sourceText = $novel
  targetFormat = "screenplay"
  styleHint = "悬疑短剧，克制现实主义"
} | ConvertTo-Json -Depth 8

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/convert `
  -ContentType "application/json; charset=utf-8" `
  -Body $body
```

运行测试：

```powershell
cd backend
mvn test
```

### 前端启动方式

当前仓库的 `fronted/` 目录为空，没有 `package.json`、构建脚本或可执行前端应用。因此本版本没有前端启动命令；评委本地复现请使用后端 API 和 `examples/` 示例文件完成。

后续如补齐前端，应先在 `fronted/` 中提交真实的包管理配置和脚本，再在 README 中补充对应命令。

## 环境变量配置

后端默认读取 `backend/src/main/resources/application.yml`，并通过环境变量覆盖配置：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile。`dev` 不启用真实 OpenAI 自动配置；`local` 可接入真实模型。 |
| `SERVER_PORT` | `8080` | 后端 HTTP 端口。 |
| `SPRING_AI_MODEL_CHAT` | `none` | Spring AI Chat 模型类型；接入 OpenAI 时通常设为 `openai`。 |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI 兼容接口地址。 |
| `OPENAI_API_KEY` | 空 | OpenAI API Key，仅本地或部署环境配置，不提交到 Git。 |
| `OPENAI_MODEL` | `gpt-4.1-mini` | Chat 模型名称。 |
| `STORY2SCRIPT_AGENT_MAX_STEPS` | `8` | Agent 最大规划/执行步数。 |

接入真实模型时可复制示例配置：

```powershell
Copy-Item backend\src\main\resources\application-local.example.yml backend\src\main\resources\application-local.yml
$env:SPRING_PROFILES_ACTIVE = "local"
$env:OPENAI_API_KEY = "<your-openai-api-key>"
cd backend
mvn spring-boot:run
```

`application-local.yml` 用于本地密钥和个人配置，已在 `.gitignore` 中忽略，不能提交。仓库只提交 `application-local.example.yml` 作为模板。

## 第三方依赖及用途

- Spring Boot：提供 Web 服务、配置管理、应用启动和测试基础设施。
- Spring Web：实现 `/api/health`、`/api/schema`、`/api/convert` HTTP API。
- Spring Validation / Jakarta Validation：校验转换请求中的必填字段。
- Spring AI OpenAI Starter：封装 OpenAI 兼容 Chat 模型调用。
- SnakeYAML：安全解析 YAML，并配合项目内 Schema 校验器检查剧本结构。
- JUnit 5、AssertJ、Spring Boot Test：覆盖 Agent、工具、API 服务和 YAML Schema 校验。

## 原创功能说明

- 小说章节解析：识别输入文本中的章节边界，为后续剧情压缩和改编提供结构。
- ReAct 风格 Agent 编排：按“分析、规划、生成、校验、修复”的步骤组织小说转剧本流程。
- 剧本 YAML Schema v1.0：固定输出顶层字段、场景类型和 beat 类型，降低模型自由输出带来的结构漂移。
- YAML 修复链路：当模型输出不满足 Schema 时，可基于校验错误进行修复。
- 开发环境 fallback：`dev` profile 下无需真实模型密钥，也能启动后端并演示接口链路。

## 示例文件

- [examples/sample-novel.txt](examples/sample-novel.txt)：原创 3 章短篇小说示例，不含受版权保护小说片段。
- [examples/sample-screenplay.yaml](examples/sample-screenplay.yaml)：符合当前剧本 YAML Schema v1.0 的结构化剧本示例。

## 知识产权提示

- 请只上传、转换和展示自己拥有版权、已获授权或处于公共领域的文本。
- 不要将受版权保护的小说、剧本、影视字幕或平台付费内容直接作为示例数据提交到仓库。
- 本仓库的 `examples/sample-novel.txt` 为原创示例文本，仅用于功能演示和本地复现。
- AI 生成结果需要人工复核；如用于公开发布、商业展示或二次创作，应确认输入素材授权、生成内容相似性和第三方模型服务条款。
- 第三方依赖的许可证和使用限制应以各依赖官方发布条款为准。

## Demo 视频

- Demo 视频链接：`TODO: https://example.com/zen-story2script-demo`

## 项目文档

- [需求文档](docs/requirements.md)
- [架构设计](docs/architecture.md)
- [MVP 工作计划](docs/mvp-workplan.md)
