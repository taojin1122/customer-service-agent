# 智能客服 Agent（CS-Agent）

一个从零搭建的**智能客服 Agent 项目**，用于学习和实践主流 Agent 开发知识体系。

- **后端**：Java 17 + Spring Boot 3.4 + Spring AI 1.0（GA）+ DeepSeek（OpenAI 兼容协议）
- **前端**：Vue 3 + Vite + TypeScript + Element Plus + Pinia
- **存储**：H2（默认零依赖）/ MySQL（生产形态）；Redis Stack（可选向量库）
- **文档**：[docs/](./docs/) 目录含架构设计、Agent 知识体系、开发流程、框架对比

---

## 一、快速开始

### 1. 准备

1. JDK 17
2. Maven 3.6+
3. Node.js 16+
4. DeepSeek API Key（[platform.deepseek.com](https://platform.deepseek.com) 获取）

### 2. 配置 API Key

**方式一：环境变量（推荐，优先级最高）**

```bash
# Windows PowerShell
$env:DEEPSEEK_API_KEY = "sk-xxxxxxxxxxxxxxxx"
```

**方式二：本地密钥文件（适合 IDE 直接启动，免去每次设环境变量）**

1. 打开 `backend/cs-agent-server/src/main/resources/application-secret.yml`
2. 在 `cs-agent.secrets.deepseek-api-key` 处填入 Key

该文件已被 `.gitignore` 忽略，不会提交到版本库。

> ⚠ **切勿把 Key 写进 `application.yml`** —— 该文件受版本控制，一旦写入明文 Key 即等同于公开泄露。
> 密钥解析优先级：`DEEPSEEK_API_KEY` 环境变量 > `application-secret.yml`。

### 3. 启动后端

```bash
cd backend
mvn clean install -DskipTests
cd cs-agent-server
mvn spring-boot:run
```

> 无需 Docker / MySQL / Redis，默认使用 H2 内存库 + 本地向量库即可完整运行。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173

### 5. 切换 MySQL（可选）

```bash
# 激活 mysql profile（先执行 sql/init.sql 建库）
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
# 或用 docker compose 一键起 MySQL + Redis Stack
docker compose up -d
```

---

## 二、演示玩法

打开聊天界面，试试这些对话：

| 用户输入 | Agent 行为 |
|---|---|
| 你好 | 走聊天意图，礼貌回应 |
| **我家水管漏水了** | 意图识别 → 调用 `create_repair_order` 工具（高权限）→ 返回工单号 |
| **查询3-2-501的物业费** | 调用 `query_bill` 工具查账单 |
| **我家的报修单进度怎么样了** | 调用 `query_repair_order` 工具（需要姓名，Agent 会追问） |
| **你们几点下班** | 调用 `query_service_hours` 工具 |
| **物业费包含哪些项目？** | 先上传知识库文档（知识库管理页），RAG 检索后回答 |

**前端会实时展示 Agent 执行过程**：意图识别 → 思考 → 工具调用 → 工具结果 → 最终回答。

---

## 三、项目结构

```
customer-service-agent/
├── backend/                      # Spring Boot 多模块后端
│   ├── cs-agent-common/          # 通用：常量、异常、结果封装
│   ├── cs-agent-llm/             # LLM 接入层：多模型注册/路由、ChatClient 封装
│   ├── cs-agent-prompt/          # Prompt 工程：提示词模板、角色设定、Few-shot
│   ├── cs-agent-tool/            # Tool Calling：工具注解、注册中心、执行器
│   ├── cs-agent-memory/          # 对话记忆：会话/消息持久化、滑动窗口上下文
│   ├── cs-agent-rag/             # RAG：文档解析、分块、Embedding、向量检索
│   ├── cs-agent-agent/           # Agent 核心：ReAct 循环引擎、意图识别、安全、指标
│   └── cs-agent-server/          # Web 启动模块：SSE 流式输出、REST API
├── frontend/                     # Vue3 前端
│   └── src/
│       ├── views/chat/           # 聊天界面（流式 + 工具过程可视化）
│       ├── views/knowledge/      # 知识库管理（上传/检索）
│       └── ...
├── docs/                         # 学习文档
├── sql/init.sql                  # MySQL 初始化脚本
└── docker-compose.yml            # MySQL + Redis Stack（生产形态）
```

---

## 四、核心架构：Agent 一次对话发生了什么

```
用户输入
  │
  ▼
┌──────────────────────────────────────────────────────┐
│ 1. 安全检测（PromptInjectionGuard）                   │
│    提示注入防护：拦截"忽略指令/扮演系统/泄露提示词"      │
└──────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────┐
│ 2. 意图识别（IntentClassifier）                       │
│    报修/账单/服务时间/转人工/闲聊 → 事件推送前端         │
└──────────────────────────────────────────────────────┘
  │
  ▼
┌──────────────────────────────────────────────────────┐
│ 3. Agent 引擎（AgentEngine）—— ReAct 循环             │
│                                                      │
│   ┌──────────┐    ┌──────────────┐    ┌───────────┐  │
│   │ 组装上下文 │───▶│ LLM 决策(JSON)│───▶│ 解析决策   │  │
│   │ 系统提示词 │    │ thought+action│    │           │  │
│   │ 记忆窗口   │    │ /finalAnswer │    │           │  │
│   │ 工具清单   │    └──────────────┘    └─────┬─────┘  │
│   │ RAG 检索   │                             │        │
│   └──────────┘                               ▼        │
│                                       action 非空?     │
│                                    ┌─────────┴─────┐   │
│                                    │是              │否 │
│                                    ▼                ▼   │
│                            ┌──────────────┐   ┌─────────┐
│                            │ 执行工具       │   │ 输出回答  │
│                            │ ToolRegistry  │   │ 持久化    │
│                            │ 结果回填上下文 │   │ 事件推送  │
│                            └──────┬───────┘   └─────────┘
│                                   └──▶ 循环，直到回答或达上限
└──────────────────────────────────────────────────────┘
  │
  ▼
SSE 流式推送：intent → think → tool_call → tool_result → answer → done
```

**Agent 开发的核心认知**：LLM 是"大脑"（负责推理决策），你的业务代码是"手脚"（工具），
ReAct 循环把两者串起来。这也是 Agent 与传统 API 开发的本质区别——**控制权从代码转移给了模型**。

---

## 五、Agent 知识体系地图

| 知识领域 | 本项目位置 | 关键概念 |
|---|---|---|
| LLM 接入 | `cs-agent-llm` | ChatModel、流式、多模型路由、Temperature |
| Prompt 工程 | `cs-agent-prompt` + `resources/prompts/` | 角色设定、任务约束、Few-shot、模板分离 |
| Tool Calling | `cs-agent-tool` | 工具定义、参数 Schema、注册、执行、高权限 |
| Agent 循环 | `cs-agent-agent`（AgentEngine） | ReAct、迭代上限、事件驱动 |
| 记忆 | `cs-agent-memory` | 短期窗口、长期持久化、上下文组装 |
| RAG | `cs-agent-rag` | 解析、分块、Embedding、向量检索、上下文注入 |
| 意图识别 | `cs-agent-agent`（IntentClassifier） | 规则/LLM 分类、实体抽取、置信度 |
| 流式输出 | `cs-agent-server`（ChatController） | SSE、逐帧渲染 |
| 安全 | `cs-agent-agent`（PromptInjectionGuard） | 提示注入、工具权限、输出过滤 |
| 可观测性 | `cs-agent-agent`（AgentMetrics） | Token 统计、调用计数、日志 |

详见 [docs/02-Agent知识体系.md](./docs/02-Agent知识体系.md)。

---

## 六、开发流程：如何给 Agent 加一个新能力

**场景：新增"查询快递柜取件码"工具**

1. 在 `cs-agent-tool` 的 `tools` 包新建工具类，加 `@Component` + `@AgentTool` 注解：

```java
@Component
public class LockerTools {
    @AgentTool(description = "按取件码查询快递柜位置与有效期", domain = "locker")
    public String query_locker_code(String code) {
        return "取件码 " + code + " 对应 2 栋大堂快递柜 A-12 格口，有效期至今日 20:00";
    }
}
```

2. 重启后端，工具自动注册（启动日志会打印工具清单），Agent 即可自主调用。

3. 如需前端展示，在系统信息接口 `/api/system/tools` 自动可见。

**新增知识库文档**：直接在知识库管理页上传即可，自动完成解析→分块→向量化。

---

## 七、常见问题

| 问题 | 解决 |
|---|---|
| 对话报"未配置 LLM 模型" | 检查 `DEEPSEEK_API_KEY` 环境变量或 application.yml |
| 工具调用不稳定 | DeepSeek 对复杂 JSON 输出偶有失误，可调低 temperature、简化工具描述 |
| 想用其他模型 | 在 `cs-agent.llm.models` 追加配置即可（OpenAI/通义/本地 Ollama 均可） |
| 知识库检索效果差 | demo 默认本地哈希向量，生产切换外部 Embedding API（application.yml 注释中有配置） |
| 端口冲突 | 修改 `server.port` 与前端 vite.config.ts 的 proxy target |

---

## 八、参考

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI 1.0 GA 发布公告](https://spring.io/blog/2025/05/20/spring-ai-1-0-0-ga-released)
- [DeepSeek API 文档](https://api-docs.deepseek.com/)
