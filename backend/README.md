# customer-service-agent backend 开发说明（全服务）

> 版本：v1.0 ｜ 日期：2026-09-20
> 定位：本文件是 backend 目录的**全局开发说明**——讲清整体架构、模块全景、跨服务约定、开发规范、本地起法。
> 模块级实现细节见各模块自己的文档（如 `cs-agent-agent/AGENTS.md`）。本文档与 `../docs/微服务拆分执行计划.md` 互补：计划讲"怎么拆"，本文档讲"现在是什么、怎么开发"。

---

## 0. 怎么读本文档
- **第一次接手 / 想搞清楚全貌** → 读 §1（状态）、§3（模块全景）、§4（架构）
- **要写代码** → 先读 §5（多租户隔离，红线）、§6（开发规范）、对应模块文档
- **本地跑起来** → 读 §7
- **想知道还差什么** → 读 §9（已知偏差）

---

## 1. 当前状态 vs 目标架构

| 维度 | 当前（2026-11-02） | 目标（设计决策） |
|---|---|---|
| 形态 | 单 Spring Boot 应用（`cs-agent-server` 唯一启动类，聚合 7 模块） | 7 逻辑服务 / **3 物理进程**（见 §3 注） |
| Java | 21（已升） | 21 |
| Spring Boot | 4.1.2 | 4.1.2 |
| Spring AI | 2.0.2 | 2.0.2 |
| 数据库 | MySQL（`application-mysql.yml`） | PostgreSQL 14+ + PGVector + RLS |
| 向量库 | `SimpleMemoryVectorStore`（内存 demo） | PGVector |
| Embedding | `LocalHashEmbeddingService`（本地哈希伪） | 真实 embedding API |
| 多租户 | 无（全仓库零 `tenant_id`） | 全配置租户化：`t_tenant` + `t_tenant_config` + `tenant_id` 隔离 + RLS 双防线（方案见根 `AGENTS.md` §1.1） |
| 服务发现 | 无 | Nacos |

> 现状盘点见 `../docs/微服务拆分执行计划.md` §1。这些代码是**资产**，迁移时按职责归属到对应服务，不重写。

---

## 2. 技术栈

- **语言/框架**：Java 21 + Spring Boot 4.1.2 + Spring AI 2.0.2 + Spring Cloud Alibaba
- **ORM**：Spring Data JPA（当前）；多租户隔离用 JPA 规范 + PostgreSQL RLS
- **服务治理**：Nacos（注册发现 + 配置中心）、Sentinel（流控熔断）、Seata（跨服务分布式事务）
- **边缘网关**：Apache APISIX（设计定，**不用 Spring Cloud Gateway**）；目前未部署，HTTP 入口暂由 `cs-agent-server` 的 web 层承担
- **内部通信**：Nacos 发现 + OpenFeign / gRPC 直连，`tenant_id`/`app_id` 请求头透传
- **向量/检索**：PGVector（向量）+ ES+IK（中文 BM25），RRF 融合 + Rerank
- **构建**：Maven 多模块（parent `backend/pom.xml`）

---

## 3. 模块全景（当前 8 模块 → 目标 7 服务）

> **物理部署说明（待你确认）**：设计文档与 W1-W3 清单定的是 **3 物理进程**——`tenant-service`、`main-app`（聚合 channel/conversation/rag/agent/metering 五个模块）、`model-gateway`。但 `../docs/微服务拆分执行计划.md` 的 P1–P5 是按"每个服务一个独立 Spring Boot 进程（7 进程）"写的。**两者冲突，M1 建议按 3 物理进程执行**（单人运维最省力），待某模块成瓶颈再独立成进程。下表按"7 逻辑服务 / 3 物理进程"呈现。

| 目标逻辑服务 | 来源模块 | 物理进程 | 提议端口 | 职责 | 代码状态 |
|---|---|---|---|---|---|
| `tenant-service` | **新建** | tenant-service（独立） | 8081 | 租户/应用管理、配额、API Key 签发、RLS 策略下发 | 无 |
| `channel-service` | **新建** | main-app | 8082 | 渠道适配：Web / 企微微客服 / 钉钉 Stream / 公众号 | 无 |
| `conversation-service` | `cs-agent-memory` + server 会话接口 | main-app | 8083 | 会话/消息存储（JPA）、对话生命周期 | 已有（memory 5 类） |
| `rag-service` | `cs-agent-rag` | main-app | 8084 | 知识库入库、PGVector 检索、ES/IK BM25、RRF 融合 | 已有（rag 10 类） |
| `agent-service` | `cs-agent-agent` + `prompt` + `tool` | main-app | 8085 | Agent 编排、意图识别、提示注入防护、工具调用 | 已有（agent 9 类） |
| `metering-service` | **新建** | main-app | 8086 | 用量计量、计费、配额扣减 | 无 |
| `model-gateway` | `cs-agent-llm` | model-gateway（独立） | 8087 | 模型注册/路由/BYOM/代管兜底；**慢调用隔离** | 已有（llm 4 类） |
| `common`（共享库，非进程） | `cs-agent-common` | — | — | 切面/异常/Result/IdUtil/`TenantContext`（新增） | 已有（4 类） |

> 注：`server` 的控制器按职责拆进各服务；APISIX 作边缘网关后 `server` 不再作统一入口（本期删除，路由由 APISIX 直达服务）。模块级细节：`cs-agent-agent/AGENTS.md` 已覆盖 agent 模块。

---

## 4. 架构与跨服务通信

```
外部渠道 / 租户 API
      │
      ▼
┌──────────────────────────────────────┐
│  Apache APISIX（边缘网关，唯一入口）    │  ← 租户鉴权 / 配额限流 / 路由 / TLS / SSE 代理
└──────────────────────────────────────┘
      │  按路由转发（tenant_id / app_id 透传）
      ▼
┌──────────────┐   ┌──────────────────────────────────────┐   ┌──────────────────┐
│tenant-service│   │  main-app（channel/conversation/rag/  │   │  model-gateway   │
│  (8081)      │   │  agent/metering 五模块，同一进程)       │   │   (8087, 独立)   │
└──────────────┘   └──────────────────────────────────────┘   └──────────────────┘
                         │            │            │
                         └── Nacos 发现 + OpenFeign/gRPC 直连（不走网关）──┘
```

- **边缘网关**：APISIX 唯一入口，做鉴权/限流/路由；**不用 Spring Cloud Gateway**（Java 网关高并发资源占用劣势明显）。
- **内部调用**：服务间经 Nacos 发现 + OpenFeign/gRPC 直连；`tenant_id`/`app_id` 必须在请求头透传（见 §5）。
- **为什么 model-gateway 必须独立进程**：模型调用 3–30s，占满业务线程池会雪崩；独立进程 + 独立线程池隔离（设计文档 §423 理由）。

---

## 5. 多租户隔离（写代码前必读 · 红线）

本平台是多租户 SaaS，隔离靠**双防线**，任一新表/新接口都必须遵守：

1. **ORM 层**：`cs-agent-common` 的 `tenant_id` 拦截器自动注入（查询/写入自动带 `tenant_id`，开发者不必手写）
2. **数据库层**：PostgreSQL **RLS（行级安全）policy**，即使代码漏写也能在 DB 层兜底

**开发约定（强制）**：
- 任何持久化实体必须有 `tenant_id` 字段
- 任何对外接口必须从 `TenantContext` 取 `tenant_id`，**禁止**从请求体/URL 参数直接当租户标识（防越权）
- 跨服务调用必须透传 `tenant_id`/`app_id` 请求头（统一 Feign 拦截器做，不要每个调用手写）
- 验收闸门：租户 A 的 token 调租户 B 的接口 → **必须 403**（交叉租户渗透测试）

> 当前状态：双防线**尚未实现**（P0/P7 待办）。写新代码时先按上述约定预留 `tenant_id`，避免后期返工。

---

## 6. 全局开发规范

### 6.1 强制代码注释（源自 `cs-agent-agent/AGENTS.md` §0，扩展到全 backend）
> 用户非 Agent / 微服务出身，代码必须讲清"为什么"，否则未来不敢改。

1. 每段逻辑写"为什么"——不写 `// 解析决策`，写 `// 解析 LLM 的 JSON：LLM 偶尔用 Markdown 包裹，先剥离再 parse，否则整轮废掉`
2. 公开方法写 Javadoc，讲清解决什么业务场景、为何这么设计
3. 反直觉写法必注释（循环上限、二次确认、先过滤后检测…）
4. 踩过的坑标注（如 model-gateway 调用必须异步 + 独立线程池）
5. 术语第一次出现用白话解释（ReAct / RLS / RAG…）
6. 改代码同步更新对应模块文档

### 6.2 分支 / 提交 / CI
- 拆分按 `../docs/微服务拆分执行计划.md` 的 P0–P8 阶段，**每阶段独立 git 提交、可回退**
- CI 红线（已在 W1-W3 清单定义）：硬编码密钥提交即红灯；`mvn test` 不过不准合并
- 提交信息写清"改了什么 + 为什么"（对应 §6.1）

---

## 7. 本地开发指引

### 7.1 环境
- **JDK 21**（必须；本机未装 21 无法编译，pom 已设 `java.version=21`）
- Maven 3.9+
- 当前可本地跑：MySQL + 内存 RAG（`cs-agent-server` 起 `CsAgentApplication`）
- 接 PGVector 需本地/云 PostgreSQL 14+ 并启用 `pgvector` 扩展

### 7.2 编译（按需）
```bash
# 编译单个模块（含其上游依赖），例：刚改了 agent 模块
mvn -q -pl cs-agent-agent -am compile

# 全量编译
mvn -q compile

# 跑测试
mvn -q test
```

### 7.3 跑起来（当前单应用）
```bash
# 启动类：cs-agent-server 的 CsAgentApplication
mvn -q -pl cs-agent-server spring-boot:run
# 或
java -jar cs-agent-server/target/cs-agent-server-*.jar
```
> AI 对话入口：`ChatController`；知识库：`KnowledgeController`。当前是 MySQL + 内存 RAG 的可运行原型。

---

## 8. 模块级文档索引
| 模块 | 文档 | 说明 |
|---|---|---|
| `cs-agent-agent` | `cs-agent-agent/AGENTS.md` | Agent 引擎、意图、提示注入防护、加工具/意图约定 |
| 其余模块 | （待补） | 建议每个模块沉淀一份 `xxx.md`，本文件只讲全局 |

---

## 9. 已知偏差与待办（优先级从高到低）
1. 🔴 **多租户隔离未做**（§5 双防线）——最高风险，P0+P7
2. 🔴 **单应用 → 微服务**（§3 物理进程数待确认）——拆分计划 P0–P8
3. 🔴 **MySQL → PostgreSQL + PGVector**——P7
4. 🟡 **内存 RAG → PGVector + 真实 embedding**——P2
5. 🟡 **APISIX 未部署**——W1-W3 清单，等云资源账号
6. 🟡 **Nacos / Sentinel / Seata 未引入**——P0

> 详细执行步骤与风险对策见 `../docs/微服务拆分执行计划.md`、`../docs/W1-W3-基础设施搭建清单.md`。
