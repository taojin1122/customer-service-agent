# cs-agent-agent 模块开发说明

> 模块定位：智能客服 Agent 的核心编排引擎。在「7 微服务」目标架构中对应 **agent-service**（独立 Spring Boot 进程）。
> 当前状态：单应用形态下作为 `cs-agent-server` 依赖的库模块；拆分计划 P3 阶段将其提升为独立进程，并入 `prompt` / `tool` 两库为内部模块。
> 关联文档：`docs/多租户智能客服机器人系统设计方案.md`、`docs/微服务拆分执行计划.md`

## 0. 强制开发规范（代码注释与可解释性）

> ⛔ **硬性要求，不可违反**：本模块涉及 Agent / LLM 编排，维护者（老板）非 Agent 开发背景，因此所有代码**必须带注释，且注释要解释"为什么这样做"，不能只写"做了什么"**。

### 注释的基本原则
1. **每段逻辑都要有"为什么"**：不要写 `// 解析决策`，要写 `// 解析 LLM 的 JSON 决策：LLM 偶尔会用 Markdown 代码块包裹 JSON，先剥离再 parse，否则会抛异常导致整轮对话失败`。
2. **公开方法写 Javadoc 说明意图与适用场景**：尤其 `AgentEngine`/`PromptInjectionGuard`/`IntentClassifier` 这类核心类，注释要讲清"它解决什么客服场景问题、为什么这么设计"。
3. **非常规 / 反直觉的写法必须注释**：例如 ReAct 循环上限、工具高风险二次确认、提示注入先过滤后检测——这些不是常识，不写注释未来不敢改。
4. **依赖外部服务 / 踩过的坑要标注**：例如"model-gateway 调用可能 3–30s，必须异步 + 独立线程池，否则占满业务线程雪崩（见拆分计划 P1）"。
5. **术语第一次出现要白话解释**：如 `ReAct`、`RAG`、`AgentDecision` 等，让不懂 Agent 的人也能读。
6. **改代码时同步更新本 `agent.md`**：任何职责/流程变化，补到对应章节，避免文档与代码脱节。

### 给 AI 协作者的约束
- 凡在本模块新增/修改 Java 代码，**默认生成带解释性注释的版本**，不要因为"代码自解释"就省略。
- 如果某段逻辑确实无法用一句话讲清"为什么"，说明它可能过于复杂，应拆函数或加示例。

## 1. 核心职责
- 接收用户对话请求，编排「记忆 + RAG 知识库 + 工具调用 + LLM」完成客服应答
- 以 **ReAct（Reason + Act）循环**驱动多轮决策，支持流式事件推送（SSE）
- 内置提示注入防护（PromptInjectionGuard）、意图识别（IntentClassifier，待接入主链路）

## 2. 目录结构与职责

| 包 / 类 | 文件 | 职责 |
|---|---|---|
| `engine.AgentEngine` | AgentEngine.java | **核心引擎**：ReAct 循环、上下文组装、LLM 决策解析、工具执行、事件流 |
| `model.ChatRequest` | ChatRequest.java | 对话请求 DTO（conversationId / message / modelId / useRag / useTools） |
| `model.AgentDecision` | AgentDecision.java | LLM 结构化决策（thought / action / actionInput / finalAnswer） |
| `model.AgentDecision` — | — | 见上 |
| `intent.IntentClassifier` | IntentClassifier.java | 规则+关键词意图识别（repair/bill/service/human/greeting），提取房号实体 |
| `intent.IntentResult` | IntentResult.java | 意图识别结果（type / desc / score / entities） |
| `security.PromptInjectionGuard` | PromptInjectionGuard.java | 提示注入防护：输入模式过滤 + 输出泄露检测 |
| `event.AgentEvent` | AgentEvent.java | 运行事件（intent/think/tool_call/tool_result/rag/answer/done/error） |
| `observability.AgentMetrics` | AgentMetrics.java | 指标埋点（可观测性） |

## 3. 主流程（AgentEngine.run）

```
用户消息
  │
  ├─ 1. ensureConversation：无 conversationId 则建会话
  ├─ 2. 组装上下文：SystemMessage(角色+工具清单+[知识库资料]) + 记忆滑动窗口 + 用户消息
  ├─ 3. ReAct 循环（上限 AgentConstants.DEFAULT_MAX_ITERATIONS）：
  │     a. chatService.chat(messages, modelId) → LLM 输出
  │     b. parseDecision：解析 JSON（兼容 Markdown 代码块包裹）
  │     c. action 非空 → toolRegistry.execute → 结果回填 messages → 回到 a
  │     d. action 为空 → finalAnswer 作为最终回答 → 退出
  └─ 4. 循环超限 → 兜底话术「超出最大尝试次数，请转人工」
```

流式输出通过 `Flux<AgentEvent>` 推送：每个 `think` / `tool_call` / `tool_result` / `rag` / `answer` / `done` 都是独立事件，前端按类型渲染（思考气泡、工具卡片、引用、流式文本）。

## 4. 依赖（当前单应用形态）
- `com.csagent.llm.ChatService` — 模型调用（拆分后为 model-gateway 远程 Feign 调用）
- `com.csagent.memory.MemoryService` — 会话/消息记忆（拆分后为 conversation-service）
- `com.csagent.rag.KnowledgeService` — 知识库检索（拆分后为 rag-service）
- `com.csagent.prompt.SystemPromptBuilder` — 系统提示词（并入本模块）
- `com.csagent.tool.ToolRegistry` / `ToolDefinition` — 工具注册与定义（并入本模块）
- `com.csagent.common` — 常量、异常、Result、IdUtil、**（待加）TenantContext**

## 5. 开发约定

### 新增一个工具
1. 在 `cs-agent-tool` 实现 `@AgentTool` 标注的工具类（参考 `BillTools` / `RepairOrderTools` / `ServiceInfoTools`）
2. 在 `ToolDefinition` 声明 `name` / `description` / `highRisk`
3. 引擎通过 `toolRegistry.execute(name, args)` 调用；`highRisk=true` 的工具需配合前端二次确认

### 新增意图
1. 在 `IntentClassifier.KEYWORDS` 增加 `type → 关键词数组`
2. 在 `describe()` 的 switch 补充中文描述
> ⚠️ 当前意图识别是**规则版演示实现**，生产应改为 LLM Few-shot 分类或专用模型（见类注释）。

### 系统提示词
- 角色设定在 `SystemPromptBuilder.buildBase(角色名, 公司名)`，当前硬编码「小易 / 易乐智慧物业」
- **生产需改为按租户配置**（多租户下每个租户有独立机器人人设）

## 6. 与设计的差距（待补齐，按拆分计划推进）

| 缺口 | 现状 | 目标 |
|---|---|---|
| 多租户 `tenant_id` 透传 | **无**（全模块 grep 零结果） | TenantContext 全链路透传；按租户隔离会话/知识/记忆 |
| 安全拦截接入主链路 | ✅ 已接入（2026-09-15） | 入口先 `checkUserInput` 拦截；输出 `checkOutputLeak` 兜底替换（见 `run()` 关卡 1/3） |
| 意图识别接入主链路 | ✅ 已接入（2026-09-15） | `run()` 前置 `classify` → 推 `intent` 事件 + 注入系统提示词 + 纯问候轻量分流（不下挂工具/RAG） |
| model 隔离 | `ChatService` 进程内调用 | 拆分 P1 后改经 Nacos + Feign 调独立 model-gateway |
| RAG / Embedding | 内存 `SimpleMemoryVectorStore` + 本地哈希伪 embedding | P2 接 PGVector + 真实 embedding API |
| 人设配置 | 硬编码「小易/易乐」 | 按租户配置（tenant-service 下发） |

## 7. 拆分到 agent-service（P3）要点
- 本模块加 `@SpringBootApplication`，`prompt` / `tool` 作为内部依赖模块
- 经 Feign 调 `model-gateway`（模型）与 `rag-service`（知识）、`conversation-service`（记忆）
- 接入 Nacos 服务发现 + Sentinel 流控；`tenant_id` / `app_id` 请求头透传
- APISIX 路由 `POST /api/agent/chat` → agent-service（SSE 流式）

---
*本文件为模块开发说明，随代码演进维护。架构级决策以 `docs/` 下设计文档为准。*
