# 本地开发与演示说明

本文档补充 README 的本地复现流程，聚焦评审、演示和开发联调时最容易出错的配置边界。

## 推荐启动顺序

1. 启动后端。
2. 确认 `GET http://localhost:8080/api/health` 返回 `ok`。
3. 启动前端。
4. 在前端粘贴 `examples/sample-novel.txt`，选择 `fast` 或 `react` 模式后转换。

## 无密钥演示

无模型服务密钥时，使用 `dev` profile。该模式不会读取或提交任何真实密钥，适合评委快速确认 API、前端、SSE 进度展示和 YAML 预览链路。

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

然后启动前端：

```powershell
cd frontend
npm install
npm run dev
```

## 真实模型联调

真实模型联调只应通过环境变量或本地忽略文件配置密钥。

```powershell
cd backend
Copy-Item src\main\resources\application-local.example.yml src\main\resources\application-local.yml
$env:SPRING_PROFILES_ACTIVE = "local"
$env:OPENAI_API_KEY = "<your-api-key>"
mvn spring-boot:run
```

注意：

- `application-local.yml` 已被 `.gitignore` 忽略，不得提交。
- `OPENAI_API_KEY`、私有 token、账号密码和个人代理地址不得写入文档或 examples。
- `application-local.example.yml` 只能保留占位符或可公开的默认值。

## 前端联调

前端默认访问 `http://localhost:8080`。如后端端口变化，可以设置：

```powershell
cd frontend
$env:VITE_API_BASE_URL = "http://localhost:8081"
npm run dev
```

后端 CORS 当前允许：

- `http://localhost:5173`
- `http://127.0.0.1:5173`

如果 Vite 分配了其他端口，应优先释放 `5173` 或调整后端 CORS 配置后再联调。

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

检查是否误提交敏感信息：

```powershell
git diff --cached
git status --short
```

提交前请确认 diff 中没有真实 API Key、访问令牌、账号密码、未授权文本或 `application-local.yml`。
