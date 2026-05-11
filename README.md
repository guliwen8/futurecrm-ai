# FutureCRM AI

一个基于 Spring Boot 3 + SQLite 的 AI 原生轻量 CRM 系统。

## 已完成的完整能力

### 业务模块
- 登录认证（Token 机制，支持 ADMIN/MANAGER/SALES/FINANCE 四种角色）
- 客户管理（CRUD + 搜索筛选 + 时间线）
- 联系人管理（CRUD + 关键决策人标记）
- 跟进记录（CRUD + 自动同步下次跟进时间）
- 销售订单（CRUD + 订单明细 + 状态流转）
- 收款管理（CRUD + 自动状态计算 + 逾期检测 + 回写订单已收金额）
- 用户管理（仅 ADMIN：创建/编辑/重置密码/启用禁用）
- 首页仪表盘（实时统计 + 逾期收款提醒）

### AI 能力
- AI 客户画像
- AI 跟进建议
- AI 跟进总结
- AI 销售话术
- AI 订单风险检查
- 全局 AI 问答
- 支持 OpenAI / 小米 MiMo 模型切换
- 无 Key 时本地规则降级
- AI 结果自动保存到 ai_insights 表

### 工程化
- Flyway 数据库自动迁移（含种子数据）
- Dockerfile + docker-compose.yml 一键部署
- backup.sh 数据库备份脚本（保留最近 30 个备份）
- 健康检查接口 GET /api/system/health
- 系统统计接口 GET /api/system/stats
- 全局异常处理 + 统一 ApiResponse 格式

## 快速启动

```bash
cd new-ai-crm
mvn spring-boot:run
```

打开 http://localhost:8080

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| sales01 | admin123 | 销售 |
| manager01 | admin123 | 管理者 |
| finance01 | admin123 | 财务 |

## AI 配置

不配置 API Key 也能运行，系统会返回本地规则建议。

```bash
# 小米 MiMo
export AI_API_KEY=你的Key
export AI_BASE_URL=https://api.xiaomimimo.com/v1
export AI_MODEL=mimo-v2-flash
export AI_API_STYLE=chat-completions

# OpenAI
export AI_API_KEY=你的Key
export AI_BASE_URL=https://api.openai.com/v1
export AI_MODEL=gpt-4.1-mini
export AI_API_STYLE=responses

mvn spring-boot:run
```

## Docker 部署

```bash
# 构建并启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止
docker-compose down
```

数据文件挂载在 Docker volume `crm-data` 中，容器重建不丢失。

## 数据库备份

```bash
./backup.sh
```

备份文件存储在 `backups/` 目录，自动保留最近 30 个。

恢复：

```bash
cp backups/futurecrm-ai-YYYYMMDD-HHMMSS.db futurecrm-ai.db
```

## 数据库

默认 SQLite 文件：`new-ai-crm/futurecrm-ai.db`

如需指定路径：

```bash
export FUTURECRM_DB=/path/to/futurecrm-ai.db
```

## API 概览

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `/api/auth/*` | 登录、退出、当前用户 |
| 客户 | `/api/customers/*` | CRUD + 时间线 |
| 联系人 | `/api/customers/:id/contacts` | 按客户管理联系人 |
| 跟进 | `/api/customers/:id/follow-records` | 按客户管理跟进 |
| 订单 | `/api/orders/*` | CRUD + 明细 |
| 收款 | `/api/receipts/*` | CRUD + 逾期查询 |
| 用户 | `/api/users/*` | 用户管理（ADMIN） |
| 系统 | `/api/system/*` | 健康检查、统计、AI 配置 |
| AI | `/api/ai/*` | 画像、建议、话术、总结、风险、问答 |
| 仪表盘 | `/api/dashboard` | 首页统计数据 |

## 技术栈

- Java 17 · Spring Boot 3.3.6
- SQLite · Flyway 自动迁移
- JDBC Template（轻量直连，无 ORM）
- 自研 Token 认证（内存会话 + 拦截器）
- 内置单页前端（HTML/CSS/JS）
- Docker 支持
