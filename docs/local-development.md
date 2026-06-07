# 本地开发与演示

本文补充 README 的本地复现流程，重点说明认证、SSE、dev fallback、真实模型联调和提交前敏感信息检查。

## 推荐启动顺序

1. 启动后端。
2. 确认 `GET http://localhost:8080/api/health` 返回 `ok`。
3. 启动前端。
4. 在前端注册或登录演示账号。
5. 粘贴 `examples/sample-novel.txt`，选择改编目标和转换模式后开始转换。

## 无密钥演示

无模型服务密钥时，使用 `dev` profile：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

`dev` profile 的特性：

- 使用 H2 内存数据库。
- 关闭 Spring AI OpenAI 自动配置。
- 使用后端 fallback 工具输出，便于演示 API、认证、SSE 和前端 YAML 展示。
- 默认注册邀请码为 `dev-invite`。
- 应用重启后演示账号会清空。

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

前端默认访问 `http://localhost:8080`。Vite 默认地址通常为 `http://localhost:5173`。

## 真实模型联调

真实模型联调只应通过环境变量或本地忽略文件保存密钥：

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

注意：

- `application-local.yml` 已被 `.gitignore` 忽略，不得提交。
- `OPENAI_API_KEY`、数据库密码、真实 token、真实邀请码和私有代理地址不得写入 README、docs、examples 或提交历史。
- 开启 PGVector RAG 时，还需要配置 embedding 模型、`SPRING_AI_VECTORSTORE_TYPE=pgvector` 和 `STORY2SCRIPT_RAG_VECTOR_STORE_ENABLED=true`。

## 前端联调

修改后端地址：

```powershell
cd frontend
$env:VITE_API_BASE_URL = "http://localhost:8081"
npm run dev
```

后端 CORS 当前允许：

- `http://localhost:5173`
- `http://127.0.0.1:5173`

如果 Vite 使用了其他端口，优先释放 `5173`，或调整后端 CORS 配置后再联调。

## 认证流程

注册请求：

```powershell
$registerBody = @{
  email = "judge@example.local"
  password = "local-demo-password"
  displayName = "评审演示"
  inviteCode = "dev-invite"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json; charset=utf-8" `
  -Body $registerBody `
  -SessionVariable session
```

登录请求：

```powershell
$loginBody = @{
  email = "judge@example.local"
  password = "local-demo-password"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json; charset=utf-8" `
  -Body $loginBody `
  -SessionVariable session
```

后端会通过 HTTP-only Cookie 保存登录态。直接调用 `/api/convert` 或 `/api/convert/stream` 时，需要带上同一个 Web session。

## 转换接口演示

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
  -WebSession $session `
  -Body $body
```

SSE 流式接口为 `POST /api/convert/stream`，前端通过原生 `fetch` 读取 `text/event-stream` 响应，并在最终 `result` 事件中取得完整 YAML。

## 常用检查命令

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

提交前检查：

```powershell
git status --short
git diff --cached
```

重点确认 diff 中没有真实 API Key、访问令牌、数据库密码、真实邀请码、未授权文本或 `application-local.yml`。
