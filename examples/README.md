# Examples

本目录只存放可以公开提交的演示材料，不包含 API Key、访问令牌、账号密码、私有代理地址、本地配置文件或未授权版权文本。

## 文件说明

- `sample-novel.txt`：原创三章短篇小说《雨夜校准》，用于演示小说正文输入。
- `sample-screenplay.yaml`：基于同一原创故事整理的结构化剧本 YAML，用于展示当前 Schema v1.0 的目标输出形态。

## 使用方式

启动后端和前端后，可以在工作台中直接粘贴 `sample-novel.txt` 的全文，标题填写“雨夜校准”，改编目标选择“短剧”，转换模式可选择“快速模式”或“完整 ReAct”。

也可以通过 PowerShell 直接调用后端接口：

```powershell
cd backend

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

如果测试账号已经注册过，把 `/api/auth/register` 换成 `/api/auth/login`，请求体只保留 `email` 和 `password` 即可。

## YAML Schema 要点

`sample-screenplay.yaml` 遵循当前后端校验器约束：

- 顶层字段固定为 `schema_version`、`work`、`adaptation`、`characters`、`plot_outline`、`scenes`、`notes`。
- `schema_version` 必须为 `"1.0"`。
- `characters`、`plot_outline`、`scenes` 必须是列表。
- 每个 scene 必须包含合法 `scene_type`：`INT`、`EXT` 或 `INT/EXT`。
- 每个 scene 的 `beats` 必须是列表。
- 每个 beat 的 `type` 必须是 `action`、`dialogue`、`parenthetical` 或 `transition`。

## 合规边界

- 新增样例必须使用原创、已授权或公共领域文本。
- 不要提交真实用户上传内容、模型服务密钥、访问令牌、内部接口地址、个人联系方式或本地私有配置。
- 如果样例来自公共领域或授权素材，必须在文件内说明来源和授权状态。
