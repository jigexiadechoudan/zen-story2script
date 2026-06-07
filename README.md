# Zen Story2Script

Zen Story2Script 是一个“小说文本转结构化剧本 YAML”的 AI Agent Web 应用。前端登录后进入工作台，用户输入至少 3 章小说文本，选择改编目标和转换模式，后端通过 Agent 完成章节解析、故事分析、场景规划、YAML 生成、Schema 校验和必要修复，前端通过 SSE 展示实时进度，最终展示可复制、可下载的结构化 YAML 剧本草稿。

当前仓库定位为 MVP：功能链路完整、可本地演示、可接入 OpenAI-compatible 模型，也支持无密钥的 `dev` profile 降级演示。

## 技术栈

后端：

- Java 21
- Spring Boot 3.5
- Spring AI 1.1.6
- Spring Web、Validation、Security、Data JPA
- Flyway
- H2 / PostgreSQL
- SnakeYAML
- 可选 Spring AI RAG + PGVector

前端：

- Vue 3.5
- Vite 6
- JavaScript 单页应用
- 原生 `fetch`
- SSE 流式进度
- `motion-v`
- `lucide-vue-next`

认证：

- 注册、登录、登出、当前用户查询
- HTTP-only Cookie
- 前端登录只负责进入工作台页面
- 后端转换接口不依赖登录态，便于单独联调和演示转换能力

## 目录

```text
zen-story2script/
  backend/       Spring Boot 后端
  frontend/      Vue + Vite 前端
  docs/          架构、开发、Schema 和需求文档
  examples/      原创示例小说与示例 YAML
```

## 快速启动

### 1. 启动后端无密钥演示模式

`dev` profile 使用 H2 内存数据库和本地 fallback，不需要真实模型密钥。

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

检查后端：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/api/schema
```

### 2. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

浏览器打开 Vite 输出的地址，通常是 `http://localhost:5173`。

本地演示注册信息可以使用：

- 邮箱：`judge@example.local`
- 密码：`local-demo-password`
- 昵称：`评审演示`
- 邀请码：`dev-invite`

`dev-invite` 只用于本地 `dev` profile 演示，不应用于公开部署。

## 真实模型联调

真实模型联调建议使用环境变量或本地忽略文件配置，不要把密钥写入仓库。

```powershell
cd backend
Copy-Item src\main\resources\application-local.example.yml src\main\resources\application-local.yml
$env:SPRING_PROFILES_ACTIVE = "local"
$env:OPENAI_API_KEY = "<your-api-key>"
$env:OPENAI_BASE_URL = "https://api.openai.com"
$env:OPENAI_MODEL = "gpt-4.1-mini"
$env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:5432/story2script"
$env:DATABASE_USERNAME = "story2script"
$env:DATABASE_PASSWORD = "<your-database-password>"
$env:AUTH_TOKEN_SECRET = "<at-least-32-random-characters>"
$env:REGISTRATION_INVITE_CODE = "<your-private-invite-code>"
mvn spring-boot:run
```

`application-local.yml` 已被 `.gitignore` 忽略，可以保存本机私有配置；仓库只提交 `application-local.example.yml` 模板。

## 转换模式

- `fast`：固定快速链路，按“章节解析 -> YAML 生成 -> Schema 校验 -> 必要时修复一次”的流程执行，优先保证速度和可用性。
- `react`：在满足自主 ReAct 条件时，由模型自主决定是否调用工具，并受超时控制；如果依赖不满足、调用失败、超时或返回 YAML 校验不通过，会自动回退到 `fast`，确保仍能产出可用结果。

自主 ReAct 条件：

- `STORY2SCRIPT_AGENT_AUTONOMOUS_ENABLED=true`
- 已配置可用的 ChatClient / OpenAI-compatible 模型
- 已加载工具回调提供器
- 当前不是 `dev` fallback
- 请求的 `conversionMode` 不是 `fast`

## 前端环境变量

前端默认访问 `http://localhost:8080`。如需修改后端地址：

```powershell
cd frontend
$env:VITE_API_BASE_URL = "http://localhost:8081"
npm run dev
```

## API 摘要

公开接口：

- `GET /api/health`：健康检查。
- `GET /api/schema`：返回前端联调用的轻量 API 契约摘要。
- `POST /api/auth/register`：注册并写入 HTTP-only Cookie。
- `POST /api/auth/login`：登录并写入 HTTP-only Cookie。
- `POST /api/convert`：同步返回 YAML、质量报告、warnings 和 agentTrace。
- `POST /api/convert/stream`：通过 SSE 返回转换状态、步骤和最终结果。

需要登录态的认证接口：

- `GET /api/auth/me`：读取当前用户。
- `POST /api/auth/logout`：清除登录 Cookie。

转换请求：

```json
{
  "title": "雨夜校准",
  "sourceText": "第一章 ...\n\n第二章 ...\n\n第三章 ...",
  "targetFormat": "short_drama",
  "styleHint": "悬疑短剧，克制现实主义",
  "conversionMode": "fast"
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `title` | 是 | 小说或改编项目标题 |
| `sourceText` | 是 | 至少 3 章小说正文 |
| `targetFormat` | 是 | `short_drama`、`screenplay`、`scene_outline` |
| `styleHint` | 否 | 风格提示 |
| `conversionMode` | 否 | `fast` 或 `react`，缺省为 `fast` |

## 环境变量

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` | Spring profile；本地无密钥演示建议用 `dev` |
| `SERVER_PORT` | `8080` | 后端端口 |
| `SPRING_AI_MODEL_CHAT` | `openai` | Chat 模型类型；`dev` 会覆盖为 `none` |
| `SPRING_AI_MODEL_EMBEDDING` | `none` | Embedding 模型类型 |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI-compatible API 地址 |
| `OPENAI_API_KEY` | 空 | 模型服务 API Key |
| `OPENAI_MODEL` | `gpt-4.1-mini` | Chat 模型名 |
| `DATABASE_URL` | H2 内存库 | 数据库连接 |
| `DATABASE_USERNAME` | `sa` | 数据库用户名 |
| `DATABASE_PASSWORD` | 空 | 数据库密码 |
| `DATABASE_DRIVER` | `org.h2.Driver` | 数据库驱动 |
| `AUTH_COOKIE_NAME` | `story2script_session` | 登录 Cookie 名称 |
| `AUTH_COOKIE_SECURE` | `false` | HTTPS 部署建议设为 `true` |
| `AUTH_TOKEN_SECRET` | 开发占位值 | 登录 token 签名密钥，真实环境必须替换 |
| `AUTH_TOKEN_TTL_SECONDS` | `604800` | 登录态有效期，单位秒 |
| `REGISTRATION_INVITE_CODE` | `dev-invite` | 注册邀请码，公开部署必须替换 |
| `STORY2SCRIPT_AGENT_AUTONOMOUS_ENABLED` | `true` | 是否允许 `react` 使用自主 ReAct 工具调用 |
| `STORY2SCRIPT_AGENT_AUTONOMOUS_TIMEOUT` | `45s` | 自主 ReAct 最大等待时间，超时后回退 `fast` |
| `STORY2SCRIPT_AGENT_MAX_INPUT_CHARS` | `12000` | 自主 ReAct 输入文本截断上限 |
| `STORY2SCRIPT_RAG_VECTOR_STORE_ENABLED` | `false` | 是否启用向量检索 |
| `STORY2SCRIPT_RAG_SYNC_ON_STARTUP` | `false` | 是否启动时同步内置知识 |
| `STORY2SCRIPT_RAG_TOP_K` | `2` | RAG 检索片段数 |
| `VITE_API_BASE_URL` | `http://localhost:8080` | 前端 API 基础地址 |

## Docker 部署

仓库提供 `docker-compose.yml`、`.env.example` 和前端 Nginx 构建配置，可用于服务器部署。

```powershell
Copy-Item .env.example .env
# 编辑 .env，填入数据库、模型、鉴权密钥等真实配置
docker compose up -d --build
```

阿里云 ECS 部署可参考 [docs/alicloud-docker-deployment.md](docs/alicloud-docker-deployment.md)。

## 验证命令

后端测试：

```powershell
cd backend
mvn test
```

前端构建：

```powershell
cd frontend
npm run build
```

## 示例文件

- [examples/sample-novel.txt](examples/sample-novel.txt)：原创 3 章短篇小说《雨夜校准》。
- [examples/sample-screenplay.yaml](examples/sample-screenplay.yaml)：符合 YAML Schema v1.0 的结构化剧本示例。
- [examples/README.md](examples/README.md)：示例使用方式和合规边界。

## 文档入口

- [需求文档](docs/requirements.md)
- [架构设计](docs/architecture.md)
- [本地开发与演示](docs/local-development.md)
- [阿里云 Docker 部署](docs/alicloud-docker-deployment.md)
- [MVP 工作计划](docs/mvp-workplan.md)
- [剧本 YAML Schema](docs/screenplay-yaml-schema.md)

## 安全与合规

- 不要提交真实 API Key、访问令牌、数据库密码、私有代理地址、真实邀请码或本地私有配置。
- 不要提交未授权小说、剧本、字幕、付费内容或真实用户上传文本。
- `examples/` 中的示例文本为原创演示材料，仅用于本地复现和功能展示。
- AI 输出是可编辑草稿，不应直接作为最终商业剧本发布；公开发布前需要人工复核授权、相似性和第三方模型服务条款。

## Demo 视频

- Demo 视频链接：`TODO`
