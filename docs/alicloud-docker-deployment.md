# 阿里云 ECS Docker 部署说明

本文档适用于：ECS 已安装 Docker，PostgreSQL 已在云服务器或云数据库上准备好。

当前部署文件是开发阶段的基线草稿，目标是先让应用在云服务器上可重复构建、启动和验证。后续正式上线前，域名、HTTPS、镜像仓库、CI/CD、日志采集、监控、备份、RAG/PGVector、数据库网络策略都可以继续调整。

当前线上演示地址：[https://www.zens2s.top/](https://www.zens2s.top/)。

## 服务器还需要什么

如果 Docker 已经可用，应用运行本身不需要再安装 Java、Maven、Node.js 或 Nginx。镜像构建会在 Docker 容器里完成。

仍需确认：

- Docker Compose 插件可用：`docker compose version`
- ECS 安全组放行 `80` 和 `443`
- PostgreSQL 对 ECS 可访问，且已创建数据库和账号
- 生产访问建议使用 HTTPS；如果没有 HTTPS，登录 Cookie 会因为 `AUTH_COOKIE_SECURE=true` 无法在浏览器里正常保存

## PostgreSQL 准备

创建数据库和账号，示例：

```sql
CREATE DATABASE story2script;
CREATE USER story2script WITH PASSWORD '<strong-password>';
GRANT ALL PRIVILEGES ON DATABASE story2script TO story2script;
```

应用启动后会通过 Flyway 自动创建当前需要的 `users` 表。

如果先不启用 RAG 向量检索，不需要安装 `pgvector`。如果后续启用，需要确保 PostgreSQL 支持 `pgvector` 扩展，并配置 DashScope embedding 相关环境变量。

## 上传代码

在服务器上放置代码，例如：

```bash
mkdir -p /opt/zen-story2script
cd /opt/zen-story2script
git clone <your-repo-url> .
```

也可以用 `scp` 或 CI 把项目目录同步到 `/opt/zen-story2script`。

## 配置环境变量

复制模板：

```bash
cp .env.example .env
chmod 600 .env
```

编辑 `.env`，至少填写：

```bash
DATABASE_URL=jdbc:postgresql://<postgres-host>:5432/story2script
DATABASE_USERNAME=story2script
DATABASE_PASSWORD=<strong-password>
OPENAI_BASE_URL=<openai-compatible-base-url>
OPENAI_API_KEY=<model-api-key>
OPENAI_MODEL=<model-name>
AUTH_TOKEN_SECRET=<at-least-32-random-characters>
REGISTRATION_INVITE_CODE=<private-invite-code>
AUTH_COOKIE_SECURE=true
```

如果 PostgreSQL 与 Docker 容器在同一台 Linux ECS 宿主机上，但 PostgreSQL 不是 Docker 容器，`localhost` 在容器里指的是容器自己，不是宿主机。建议使用 ECS 内网 IP、RDS 内网地址，或把 PostgreSQL 也放入同一个 Docker 网络。

## 构建并启动

```bash
docker compose build
docker compose up -d
```

查看状态：

```bash
docker compose ps
docker compose logs -f backend
```

健康检查：

```bash
curl http://127.0.0.1/api/health
```

前端容器会把 `/api/` 反向代理到后端容器 `backend:8080`，所以浏览器访问同一个域名即可。

## HTTPS 说明

生产环境推荐让用户通过 HTTPS 访问，例如：

- 阿里云负载均衡/全站加速/CDN 终止 HTTPS，再转发到 ECS 的 `80`
- 或在 ECS 上额外部署宿主机 Nginx/Caddy 处理证书，再反代到 Docker 暴露的 `80`

只要浏览器地址栏是 `https://你的域名`，`AUTH_COOKIE_SECURE=true` 就可以正常工作。

临时 HTTP 冒烟测试可以把 `.env` 中 `AUTH_COOKIE_SECURE=false`，但正式上线必须改回 `true` 并使用 HTTPS。

## 常用运维命令

更新代码并重启：

```bash
git pull
docker compose build
docker compose up -d
```

查看后端日志：

```bash
docker compose logs -f backend
```

查看前端/Nginx 日志：

```bash
docker compose logs -f frontend
```

重启服务：

```bash
docker compose restart
```

停止服务：

```bash
docker compose down
```

## 首次上线建议

第一次上线先保持这些配置：

```bash
STORY2SCRIPT_RAG_VECTOR_STORE_ENABLED=false
STORY2SCRIPT_RAG_SYNC_ON_STARTUP=false
SPRING_AI_MODEL_EMBEDDING=none
SPRING_AI_VECTORSTORE_TYPE=none
```

确认注册、登录、生成、SSE 进度流都正常后，再开启 PGVector 和 DashScope embedding。
