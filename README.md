# Zen Story2Script

Zen Story2Script 是一个“小说文本转结构化剧本 YAML”的 AI Agent MVP。系统接收原创小说文本，经过章节解析、故事分析、场景规划、剧本 YAML 生成、Schema 校验和必要修复，输出可被前端预览、复制、下载或继续人工编辑的结构化剧本草稿。

## 技术栈

- 后端：Java 21、Spring Boot 3.5、Spring Web、Spring Validation
- AI 接入：Spring AI 1.0、OpenAI-compatible Chat 模型适配
- YAML：SnakeYAML，用于剧本 YAML 安全解析和 Schema 校验
- 前端：Vue 3、Vite 6、原生 Fetch、SSE 流式进度展示
- 构建与测试：Maven、JUnit 5、AssertJ、npm

## 本地复现

### 前置要求

- JDK 21
- Maven 3.9+ 或兼容版本
- Node.js 20+ 与 npm
- 可选：OpenAI-compatible API Key。没有密钥时请使用后端 `dev` profile 演示降级链路。

### 后端启动方式

无密钥演示模式：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

真实模型联调模式：

```powershell
cd backend
Copy-Item src\main\resources\application-local.example.yml src\main\resources\application-local.yml
$env:SPRING_PROFILES_ACTIVE = "local"
$env:OPENAI_API_KEY = "<your-api-key>"
mvn spring-boot:run
```

默认端口为 `8080`。启动后可检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/api/schema
```

使用示例小说调用非流式转换接口：

```powershell
$novel = Get-Content ..\examples\sample-novel.txt -Raw -Encoding utf8
$body = @{
  title = "雨夜校准"
  sourceText = $novel
  targetFormat = "short_drama"
  styleHint = "悬疑短剧，克制现实主义"
  conversionMode = "fast"
} | ConvertTo-Json -Depth 8

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/convert `
  -ContentType "application/json; charset=utf-8" `
  -Body $body
```

运行后端测试：

```powershell
cd backend
mvn test
```

### 前端启动方式

前端位于 `frontend/`，本地开发服务默认使用 `http://localhost:5173`，后端 API 默认指向 `http://localhost:8080`。

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 Vite 输出的本地地址，通常是 `http://localhost:5173`。前端会优先调用后端流式接口 `POST /api/convert/stream`，并在浏览器不支持流式读取时回退到 `POST /api/convert`。

构建与预览：

```powershell
cd frontend
npm run build
npm run preview
```

如需修改前端访问的后端地址：

```powershell
cd frontend
$env:VITE_API_BASE_URL = "http://localhost:8080"
npm run dev
```

## 环境变量配置

后端默认读取 `backend/src/main/resources/application.yml`，并通过环境变量覆盖配置：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` | Spring profile。演示降级建议设为 `dev`；真实模型联调使用 `local`。 |
| `SERVER_PORT` | `8080` | 后端 HTTP 端口。 |
| `SPRING_AI_MODEL_CHAT` | `openai` | Spring AI Chat 模型类型；`dev` profile 会覆盖为 `none`。 |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI-compatible 接口地址。 |
| `OPENAI_API_KEY` | 空 | 模型服务 API Key，仅在本地或部署环境配置，不提交到 Git。 |
| `OPENAI_MODEL` | `gpt-4.1-mini` | Chat 模型名称，可按实际服务支持情况调整。 |
| `STORY2SCRIPT_AGENT_MAX_STEPS` | `8` | Agent 最大规划/执行步数。 |
| `VITE_API_BASE_URL` | `http://localhost:8080` | 前端请求后端 API 的基础地址。 |

`application-local.yml` 用于本地密钥和个人模型配置，已在 `.gitignore` 中忽略，不能提交。仓库只提交 `backend/src/main/resources/application-local.example.yml` 作为模板。不要把 API Key、访问令牌、私有代理地址或个人账号信息写入 README、examples、docs 或提交历史。

## API 摘要

- `GET /api/health`：后端存活检查，返回 `status=ok`。
- `GET /api/schema`：返回前端联调用的输入/输出契约摘要。
- `POST /api/convert`：同步返回剧本 YAML、Schema 版本、警告、质量报告和 Agent 执行轨迹。
- `POST /api/convert/stream`：通过 SSE 返回转换状态、步骤事件和最终结果，供前端展示进度。

转换请求字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `title` | 是 | 小说标题。 |
| `sourceText` | 是 | 小说正文；前端要求至少 3 章且正文不能过短。 |
| `targetFormat` | 是 | 目标格式，例如 `short_drama`、`screenplay`、`scene_outline`。 |
| `styleHint` | 否 | 风格提示。 |
| `conversionMode` | 否 | `fast` 或 `react`；缺省为 `fast`。 |

## 第三方依赖及用途

- Spring Boot：提供应用启动、配置管理、Web 服务和测试基础设施。
- Spring Web：实现 REST API、SSE 流式接口和 CORS 支持。
- Spring Validation / Jakarta Validation：校验转换请求中的必填字段。
- Spring AI OpenAI Starter：封装 OpenAI-compatible Chat 模型调用。
- SnakeYAML：安全解析 YAML，并配合项目内 Schema 校验器检查剧本结构。
- Vue 3：实现小说输入、模式选择、YAML 预览、复制下载和中英文 UI。
- Vite：提供前端开发服务、构建和本地预览。
- JUnit 5、AssertJ、Spring Boot Test：覆盖 Agent、工具、API 服务和 YAML Schema 校验。

## 原创功能说明

- 小说章节解析：识别中文章节标题和英文 `Chapter N`，为剧情压缩和改编提供结构。
- 双模式转换：`fast` 适合快速联调和 demo，`react` 执行多步分析、规划、生成和校验。
- ReAct 风格 Agent 编排：按“章节解析、故事分析、场景规划、YAML 生成、YAML 校验、YAML 修复”的步骤组织链路。
- 剧本 YAML Schema v1.0：固定输出顶层字段、场景类型和 beat 类型，降低模型自由输出带来的结构漂移。
- SSE 进度展示：前端可以实时展示转换状态和 Agent 步骤。
- 开发环境 fallback：`dev` profile 下无需真实模型密钥，也能启动后端并演示接口链路。

## 示例文件

- [examples/sample-novel.txt](examples/sample-novel.txt)：原创 3 章短篇小说示例，不含受版权保护小说片段。
- [examples/sample-screenplay.yaml](examples/sample-screenplay.yaml)：符合当前剧本 YAML Schema v1.0 的结构化剧本示例。
- [examples/README.md](examples/README.md)：样例使用方式、合规边界和校验说明。

## 知识产权提示

- 请只上传、转换和展示自己拥有版权、已获授权或处于公共领域的文本。
- 不要将受版权保护的小说、剧本、影视字幕、平台付费内容或未授权网文片段直接作为示例数据提交到仓库。
- 本仓库的 `examples/sample-novel.txt` 为原创示例文本，仅用于功能演示和本地复现。
- AI 生成结果需要人工复核；如用于公开发布、商业展示或二次创作，应确认输入素材授权、生成内容相似性和第三方模型服务条款。
- 第三方依赖的许可证和使用限制应以各依赖官方发布条款为准。

## Demo 视频

- Demo 视频链接：`TODO: https://example.com/zen-story2script-demo`

## 项目文档

- [需求文档](docs/requirements.md)
- [架构设计](docs/architecture.md)
- [MVP 工作计划](docs/mvp-workplan.md)
- [本地开发与演示说明](docs/local-development.md)
