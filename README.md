# FutureCRM AI

一个基于 Spring Boot + SQLite 的 AI 原生轻量 CRM MVP。

## 已完成能力

- 登录认证
- SQLite 自动建表
- 客户 CRUD API
- 联系人 CRUD API
- 跟进记录 CRUD API
- 销售订单 CRUD API
- 首页仪表盘 API
- AI 客户画像
- AI 跟进建议
- AI 跟进总结
- AI 销售话术
- AI 订单风险检查
- 全局 AI 问答
- 内置静态管理界面

## 启动

```bash
cd new-ai-crm
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-18.jdk/Contents/Home mvn spring-boot:run
```

打开：

```text
http://localhost:8080
```

默认账号：

```text
admin / admin123
```

## AI 配置

不配置 API Key 也能运行，系统会返回本地规则建议。

### 小米 MiMo

```bash
export XIAOMI_API_KEY=你的小米MiMoKey
export AI_BASE_URL=https://api.xiaomimimo.com/v1
export AI_MODEL=mimo-v2-flash
export AI_API_STYLE=chat-completions
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-18.jdk/Contents/Home mvn spring-boot:run
```

MiMo 兼容接口常见认证头有两种：`Authorization: Bearer <key>` 和 `api-key: <key>`。项目会同时发送这两个头，以兼容不同网关。

### OpenAI

```bash
export AI_API_KEY=你的OpenAIKey
export AI_BASE_URL=https://api.openai.com/v1
export AI_MODEL=gpt-4.1-mini
export AI_API_STYLE=responses
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-18.jdk/Contents/Home mvn spring-boot:run
```

## 数据库

默认 SQLite 文件：

```text
new-ai-crm/futurecrm-ai.db
```

如需指定路径：

```bash
export FUTURECRM_DB=/path/to/futurecrm-ai.db
```

## 当前阶段说明

这是第一版可运行 MVP。为了快速上线，前端暂时使用 Spring Boot 内置静态页面，后端接口已按 REST 风格设计，后续可以平滑替换为 Vue 3 + Element Plus 前端。
