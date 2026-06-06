# Examples

本目录只放可公开提交的演示素材，不包含 API Key、账号、令牌、私有代理地址或任何未授权版权文本。

## 文件说明

- `sample-novel.txt`：原创三章短篇小说《雨夜校准》，用于演示小说输入。文本不是摘录、改写或翻译自受版权保护作品。
- `sample-screenplay.yaml`：基于同一原创故事整理的结构化剧本 YAML，用于演示当前 Schema v1.0 的目标输出形态。

## 使用方式

后端启动后，可以把 `sample-novel.txt` 作为 `sourceText` 调用转换接口，也可以在前端工作台中直接粘贴文本。

PowerShell 示例：

```powershell
cd backend
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

## YAML Schema 要点

`sample-screenplay.yaml` 遵循当前后端校验器约束：

- 顶层字段固定为 `schema_version`、`work`、`adaptation`、`characters`、`plot_outline`、`scenes`、`notes`。
- `schema_version` 必须为 `"1.0"`。
- `scenes` 必须是列表。
- 每个 scene 必须包含合法 `scene_type`：`INT`、`EXT` 或 `INT/EXT`。
- 每个 scene 的 `beats` 必须是列表。
- 每个 beat 的 `type` 必须是 `action`、`dialogue`、`parenthetical` 或 `transition`。

## 合规边界

- 新增样例时必须使用原创、已授权或公共领域文本。
- 不要提交真实用户上传内容、模型服务密钥、访问令牌、内部接口地址、个人联系方式或本地配置文件。
- 如样例来自公共领域或授权素材，必须在文件内说明来源和授权状态。
