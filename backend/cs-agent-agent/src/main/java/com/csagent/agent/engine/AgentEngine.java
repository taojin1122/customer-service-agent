package com.csagent.agent.engine;

import com.csagent.agent.event.AgentEvent;
import com.csagent.agent.intent.IntentClassifier;
import com.csagent.agent.intent.IntentResult;
import com.csagent.agent.model.AgentDecision;
import com.csagent.agent.model.ChatRequest;
import com.csagent.agent.security.PromptInjectionGuard;
import com.csagent.common.constant.AgentConstants;
import com.csagent.common.exception.BusinessException;
import com.csagent.llm.service.ChatService;
import com.csagent.memory.service.MemoryService;
import com.csagent.prompt.builder.SystemPromptBuilder;
import com.csagent.rag.service.KnowledgeService;
import com.csagent.tool.definition.ToolDefinition;
import com.csagent.tool.registry.ToolRegistry;
import com.csagent.tool.result.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 核心引擎：用 ReAct（Reason + Act，推理 + 行动）循环驱动一次客服问答。
 *
 * <p><b>白话解释 ReAct 是什么</b>：客服机器人不是"问一句答一句"那么简单。它经常需要先"想一下"（Reason），
 * 再决定"要不要查点什么"（Act，比如去查账单、报修进度），拿到结果再想，最后才给出最终回答。
 * ReAct 就是把"思考→行动→观察结果→再思考"这个过程循环起来，直到能回答用户为止。
 * 这是当前 LLM Agent 最主流的编排范式，比"一次性直接回答"能处理需要查数据的复杂问题。</p>
 *
 * <p><b>为什么用流式（Flux / SSE）</b>：大模型生成回答是"一个字一个字蹦出来"的，可能要好几秒。
 * 如果等它全部生成完再一次性返回，用户会盯着空白屏幕干等，体验很差。所以用 {@code Flux<AgentEvent>} 把
 * 思考、工具调用、工具结果、最终回答分别作为独立事件推给前端，前端就能像打字机一样实时渲染。</p>
 *
 * <p><b>为什么有循环上限（DEFAULT_MAX_ITERATIONS）</b>：大模型偶尔会"钻牛角尖"——反复调用工具却始终得不出答案，
 * 既烧钱又卡住用户。设一个上限，超了就兜底转人工，保护成本和体验。</p>
 *
 * <p><b>主链路的两道前置关卡（本文件已接入）</b>：
 * 1. 提示注入防护 {@link PromptInjectionGuard}：用户话进模型之前先过安全闸门，挡掉"忽略指令/套提示词"等攻击。
 * 2. 意图识别 {@link IntentClassifier}：先判断用户想干嘛（报修/账单/问候…），用于业务/闲聊分流，并把意图告诉模型。</p>
 *
 * <p>一次对话的完整流程：
 * <pre>
 * 1. 安全闸门：提示注入检测（不通过直接拒答，不调模型）
 * 2. 意图识别：判断业务意图，推事件 + 注入系统提示词
 * 3. 组装上下文（系统提示词=角色人设 + 意图 + 记忆 + 工具清单 + 知识库资料）
 * 4. 调 LLM → 它返回结构化决策 JSON（thought 思考 / action 要调用的工具 / finalAnswer 最终回答）
 * 5. 若 action 非空：执行工具 → 把结果回填进上下文 → 回到步骤 4（继续想）
 * 6. 若 finalAnswer 非空：输出前再过一次泄露检测 → 推给前端
 * </pre>
 */
@Slf4j
@Service
public class AgentEngine {

    // 以下依赖通过构造器注入（Spring 推荐做法，便于测试与解耦）。
    // 它们当前是"进程内调用"，拆分到微服务后（见 docs/微服务拆分执行计划.md）会变成远程 Feign 调用，但本引擎接口不变。
    private final ChatService chatService;          // 调大模型（拆分后走 model-gateway）
    private final MemoryService memoryService;       // 会话/消息记忆（拆分后走 conversation-service）
    private final ToolRegistry toolRegistry;         // 工具注册表（账单/报修/服务信息等）
    private final KnowledgeService knowledgeService; // 知识库检索（拆分后走 rag-service）
    private final SystemPromptBuilder systemPromptBuilder; // 系统提示词组装（角色人设）
    // 安全与意图：这两个组件本就是为"前置拦截/分流"设计的，必须在 run() 最前面调用，而不是等模型返回后。
    private final PromptInjectionGuard promptInjectionGuard; // 提示注入防护（入口闸门）
    private final IntentClassifier intentClassifier;         // 意图识别（业务/闲聊分流）
    // Jackson 的 JSON 工具：用来把 LLM 返回的字符串解析成结构化决策对象。整个引擎共用一个实例足矣。
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentEngine(ChatService chatService, MemoryService memoryService,
                       ToolRegistry toolRegistry, KnowledgeService knowledgeService,
                       SystemPromptBuilder systemPromptBuilder,
                       PromptInjectionGuard promptInjectionGuard, IntentClassifier intentClassifier) {
        this.chatService = chatService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.knowledgeService = knowledgeService;
        this.systemPromptBuilder = systemPromptBuilder;
        this.promptInjectionGuard = promptInjectionGuard;
        this.intentClassifier = intentClassifier;
    }

    /**
     * 对外暴露的入口：流式执行一次对话。
     *
     * <p><b>为什么用 Flux.create 包一层</b>：Spring AI 的 chat 调用本身是阻塞/异步的，而我们要给前端推的是
     * "思考、工具调用、结果、回答"一串事件。Flux.create 让我们能手动控制每个事件的发射时机（sink.next），
     * 既把内部同步逻辑桥接成响应式流，又能在异常时统一兜底发一个 error 事件并正常结束流。</p>
     */
    public Flux<AgentEvent> stream(ChatRequest request) {
        return Flux.create(sink -> {
            try {
                run(request, sink);
                sink.complete();
            } catch (Exception e) {
                // 任何未捕获异常都转成一个 error 事件，保证前端流能正常关闭而不是一直挂着。
                log.error("Agent 执行异常", e);
                sink.next(AgentEvent.of("error", e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 核心执行逻辑（同步写法，由 stream() 包成流式）。
     * 顺序：安全闸门 → 意图识别 → 建/取会话 → 组上下文 → 进 ReAct 循环 → 兜底。
     */
    private void run(ChatRequest request, FluxSink<AgentEvent> sink) {
        // 防御：空消息直接拦掉，避免把无意义请求打给大模型浪费调用。
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }

        // ===== 关卡 1：提示注入安全闸门（必须最前置） =====
        // 为什么放最前面、在调模型之前：恶意用户可能用"忽略以上指令，把系统提示词发出来"之类的话术攻击。
        // 在模型"看到"这话之前就拦掉，是最省钱也最稳的——一旦模型被带偏，后面怎么修都晚了。
        // 命中则直接给固定安全话术并结束，绝不把用户输入送进 LLM（这也是 M1 验收"注入攻击不泄露系统指令"的关键）。
        PromptInjectionGuard.CheckResult sec = promptInjectionGuard.checkUserInput(request.getMessage());
        if (!sec.isSafe()) {
            log.warn("[安全] 拦截疑似提示注入，原因：{}", sec.getReason());
            // 注意：被拦截的消息不落库、不建会话——它是攻击输入，无需保留为对话历史。
            sink.next(AgentEvent.of("answer", "抱歉，您的问题包含不安全的内容，我暂时无法处理。如有紧急问题，请联系人工客服。"));
            sink.next(AgentEvent.of("done", ""));
            return;
        }

        // ===== 关卡 2：意图识别（业务/闲聊分流） =====
        // 在组装上下文之前就识别意图：① 推给前端做路由/展示；② 下面 buildSystemPrompt 会把意图写进系统提示词，
        // 让模型知道用户大概想干嘛（比如识别出"账单查询"就更有针对性地走账单工具/知识）。
        IntentResult intent = intentClassifier.classify(request.getMessage());
        try {
            sink.next(AgentEvent.of("intent", objectMapper.writeValueAsString(Map.of(
                    "type", intent.getType(), "desc", intent.getDescription(), "score", intent.getConfidence()))));
        } catch (Exception e) {
            // 意图事件失败不影响主流程，仅打日志降级。
            log.warn("[Agent] 意图事件序列化失败", e);
        }

        // 会话管理：前端若没带 conversationId（首轮对话），就新建一个；带了就校验它确实存在。
        // 为什么校验存在：防止用户随便编一个 id 去"碰"别人的会话（多租户隔离的一道基础防线）。
        String conversationId = ensureConversation(request);

        // 组装消息上下文。messages 就是发给大模型的"完整对话历史"，顺序很重要：系统提示词必须最前。
        // 用滑动窗口而非全量历史：对话越长，token 越贵、模型越容易跑偏，所以记忆层只回放最近若干轮。
        List<Message> messages = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(request, intent, sink);
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(memoryService.buildContextMessages(conversationId));

        // 当前这轮的用户消息，加入上下文，同时落库（记忆）供后续轮次回放。
        String userMessage = request.getMessage();
        messages.add(new UserMessage(userMessage));
        memoryService.appendMessage(conversationId, AgentConstants.ROLE_USER, userMessage, null);

        // ReAct 主循环：每轮"调模型→解析决策→要么调工具要么回答"。
        int iterations = 0;
        String finalAnswer = null;
        while (iterations < AgentConstants.DEFAULT_MAX_ITERATIONS) {
            iterations++;
            log.info("[Agent] 第 {} 轮循环开始，消息数：{}", iterations, messages.size());

            // 4.1 调 LLM 拿决策。request.getModelId() 支持租户自选模型（BYOM），为 null 时用默认模型。
            ChatResponse response = chatService.chat(messages, request.getModelId());
            // 防御：模型偶尔返回空输出，这里兜底成空串，避免下面解析时 NPE。
            String text = response.getResult() != null ? response.getResult().getOutput().getText() : "";
            AgentDecision decision = parseDecision(text);

            // 4.2 把"思考过程"推给前端。thought 是 LLM 自己写的推理说明，展示出来能让用户觉得"机器人在认真想"。
            sink.next(AgentEvent.of("think", decision.getThought() != null ? decision.getThought() : ""));

            // 4.3 分两种情况：决策里带了 action（要调工具）就走工具分支；否则当作最终回答。
            if (decision.getAction() != null && !decision.getAction().isBlank()) {
                String toolName = decision.getAction();
                // actionInput 可能为空（有些工具不需要参数），用空 Map 兜底，避免执行时空指针。
                Map<String, Object> args = decision.getActionInput() != null ? decision.getActionInput() : Map.of();
                ToolDefinition def = toolRegistry.getDefinition(toolName);
                if (def == null) {
                    // 工具不存在（模型"幻想"出一个不存在的工具名）：不能让循环崩，温和地提示模型换个方式，继续下一轮。
                    String msg = "工具不存在：" + toolName;
                    log.warn("[Agent] {}", msg);
                    messages.add(new AssistantMessage(text));
                    messages.add(new UserMessage(msg + "，请尝试其他工具或直接回答用户"));
                    sink.next(AgentEvent.of("tool_result", "{\"tool\":\"" + toolName + "\",\"error\":\"工具不存在\"}"));
                    continue;
                }

                // 推"工具调用"事件给前端：展示机器人正在调哪个工具、参数是什么。
                // highRisk 标记：高风险工具（如"发起报修"会写数据）前端要弹二次确认，避免模型擅自操作。
                String argsJson;
                try {
                    argsJson = objectMapper.writeValueAsString(args);
                } catch (Exception e) {
                    // 参数里若有无法序列化的对象，退化为 toString，保证事件能发出去（降级而非崩溃）。
                    argsJson = String.valueOf(args);
                }
                sink.next(AgentEvent.of("tool_call", "{\"tool\":\"" + toolName + "\",\"description\":\""
                        + safe(def.getDescription()) + "\",\"args\":" + argsJson + ",\"highRisk\":" + def.isHighRisk() + "}"));

                // 4.4 真正执行工具（比如查账单）。同一份 args 传给工具实现。
                log.info("[Agent] 调用工具：{} 参数：{}", toolName, args);
                ToolResult result = toolRegistry.execute(toolName, args);

                // 推"工具结果"事件：把工具返回的成功/失败、输出/错误暴露给前端，让用户看到机器人"查到了什么"。
                String resultJson;
                try {
                    resultJson = objectMapper.writeValueAsString(Map.of(
                            "tool", toolName,
                            "success", result.isSuccess(),
                            "output", result.getOutput() != null ? result.getOutput() : result.getError()
                    ));
                } catch (Exception e) {
                    resultJson = "{\"tool\":\"" + toolName + "\"}";
                }
                sink.next(AgentEvent.of("tool_result", resultJson));

                // 4.5 回填上下文——这是 ReAct 能"连续思考"的关键！
                //     必须同时把"我刚才决定调了 X（AssistantMessage）"和"工具返回了 Y（ToolResponseMessage）"
                //     加回 messages，下一轮调 LLM 时它才"看得到"自己做了什么、拿到了什么，否则它会失忆、重复调用。
                messages.add(new AssistantMessage(text));
                // 2.0 起 ToolResponseMessage 构造器改为 protected，统一走 builder 构建
                messages.add(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                "call_" + iterations, toolName,
                                result.isSuccess() ? result.getOutput() : "错误：" + result.getError())))
                        .build());
                // 工具结果也落库，作为记忆的一部分，保证多轮对话里机器人记得自己查过什么。
                memoryService.appendMessage(conversationId, AgentConstants.ROLE_TOOL,
                        result.isSuccess() ? result.getOutput() : result.getError(), toolName);
            } else {
                // action 为空 = 模型认为已经能回答了，用 finalAnswer 收尾。
                // 若 finalAnswer 也为空（极少数情况），退回模型原始输出 text，保证至少有话回给用户。
                finalAnswer = decision.getFinalAnswer() != null ? decision.getFinalAnswer() : text;

                // ===== 关卡 3：输出泄露检测（模型万一被绕过，最后再查一次） =====
                // 输入闸门不是 100% 可靠，攻击者可能用复杂话术骗模型吐出系统提示词/知识库标记。
                // 这里在推给前端之前最后兜底：一旦检测到泄露，替换为安全话术，绝不让敏感内容到达用户。
                if (promptInjectionGuard.checkOutputLeak(finalAnswer)) {
                    log.warn("[安全] 检测到输出泄露系统提示词，已替换");
                    finalAnswer = "抱歉，我暂时无法提供该信息，请联系人工客服。";
                }

                memoryService.appendMessage(conversationId, AgentConstants.ROLE_ASSISTANT, finalAnswer, null);
                sink.next(AgentEvent.of("answer", finalAnswer));
                sink.next(AgentEvent.of("done", ""));
                log.info("[Agent] 完成回答（{} 轮），会话：{}", iterations, conversationId);
                return;
            }
        }

        // 5. 循环触顶兜底：超过最大轮数还没答出来（多半是工具反复失败或模型钻牛角尖），
        //    给一个友好兜底话术并引导转人工，不让对话卡死。这是引擎自带文本，不存在泄露风险，无需再过泄露检测。
        String fallback = "抱歉，处理您的问题时超出了最大尝试次数，请稍后重试或转人工客服。";
        memoryService.appendMessage(conversationId, AgentConstants.ROLE_ASSISTANT, fallback, null);
        sink.next(AgentEvent.of("answer", fallback));
        sink.next(AgentEvent.of("done", ""));
    }

    /**
     * 解析/创建会话。
     * <p>为什么分两种：① 首轮没 id → 新建（大模型客服每次新对话都该有独立会话，便于后续按会话查历史）；
     * ② 带了 id 但库里没有 → 直接报错，而不是悄悄新建，防止用户用任意字符串"嗅探"出空白会话。</p>
     */
    private String ensureConversation(ChatRequest request) {
        if (request.getConversationId() == null || request.getConversationId().isBlank()) {
            return memoryService.createConversation(request.getModelId()).getId();
        }
        if (memoryService.getConversation(request.getConversationId()).isEmpty()) {
            throw new BusinessException("会话不存在：" + request.getConversationId());
        }
        return request.getConversationId();
    }

    /**
     * 组装系统提示词（SystemMessage 的内容）。这是给大模型立的"人设 + 能力说明书"。
     * <p>为什么要把工具清单和知识库也写进系统提示词：大模型本身不会查数据库，它只"看得懂"你塞给它的文字。
     * 所以得把"你有哪些工具可用""你该参考哪些知识库资料"用文字告诉它，它才知道什么时候该调工具、该引用哪段资料。</p>
     *
     * @param intent 已识别的用户意图，写入提示词让模型回答更有针对性；同时用于"纯问候不下挂工具/知识"的轻量分流。
     */
    private String buildSystemPrompt(ChatRequest request, IntentResult intent, FluxSink<AgentEvent> sink) {
        StringBuilder prompt = new StringBuilder();
        // TODO 多租户：当前"小易/易乐智慧物业"是硬编码人设，生产应改为按租户从 tenant-service 下发，
        // 否则所有租户的机器人都叫同一个名字、自称同一家公司，明显不对。
        prompt.append(systemPromptBuilder.buildBase("小易", "易乐智慧物业"));

        // 把识别出的意图告诉模型：让它知道用户大概想干嘛，回答更聚焦（如"账单查询"就优先走账单相关工具/知识）。
        prompt.append("\n\n[用户意图] ").append(intent.getDescription())
                .append("（类型=").append(intent.getType())
                .append("，置信度=").append(String.format("%.2f", intent.getConfidence())).append("）");

        // 业务/闲聊分流：纯问候类意图（如"你好""在吗"）几乎不需要查知识库或调工具，
        // 这种场景直接走轻量应答，省一次 RAG 检索和工具清单注入，既快又省钱。
        // 仅当请求未显式强制 useRag/useTools 时才生效——若上层明确要带工具/知识，尊重显式开关。
        boolean pureGreeting = "greeting".equals(intent.getType()) && intent.getConfidence() >= 0.8;
        boolean enableTools = (request.getUseTools() == null || request.getUseTools()) && !pureGreeting;
        boolean enableRag = (request.getUseRag() == null || request.getUseRag()) && !pureGreeting;

        // 工具清单：把注册表里的工具名+描述拼成文字，告诉模型"你可以调用这些"。
        if (enableTools) {
            List<ToolDefinition> tools = toolRegistry.listDefinitions();
            StringBuilder sb = new StringBuilder();
            for (ToolDefinition t : tools) {
                sb.append("- ").append(t.getName()).append("：").append(t.getDescription()).append("\n");
            }
            prompt.append("\n\n[可用工具]\n").append(sb);
        }

        // RAG 知识库上下文：把"和用户问题最相关的知识片段"塞进提示词，让模型基于真实资料回答，减少胡说（幻觉）。
        // knowledgeService.list() 为空说明该租户还没建知识库，直接跳过。
        if (enableRag && !knowledgeService.list().isEmpty()) {
            // 取 top-3 最相关片段：太多会撑爆上下文、稀释重点；太少可能漏掉答案。3 是客服场景的经验值。
            String knowledgeContext = knowledgeService.buildKnowledgeContext(request.getMessage(), 3);
            if (!knowledgeContext.isBlank()) {
                prompt.append("\n\n[知识库资料]\n").append(knowledgeContext);
                // 推一个 rag 事件给前端，展示"机器人参考了哪些资料"（可溯源，也是 M1 验收点之一）。
                // 只取前 300 字预览，避免把整段知识灌进事件流拖慢前端。
                String ragEvent = "{\"chunks\":\"" + safe(knowledgeContext.substring(0,
                        Math.min(300, knowledgeContext.length()))) + "\"}";
                sink.next(AgentEvent.of("rag", ragEvent));
            }
        }
        return prompt.toString();
    }

    /**
     * 预留的 RAG 事件重放入口（当前未在主链路使用，仅打日志）。
     * <p>保留原因：未来若要把"知识检索"做成独立事件节点（与 buildSystemPrompt 里的内联检索解耦），这里有现成钩子。
     * 现阶段检索直接嵌在系统提示词里，简单够用，故本方法暂为占位。</p>
     */
    private void sinkRagEvent(ChatRequest request, String context) {
        log.info("[Agent] RAG 检索注入：{} 字符", context.length());
    }

    /**
     * 把 LLM 返回的字符串解析成结构化决策（AgentDecision）。
     * <p><b>为什么需要这一步</b>：我们让模型按约定格式输出 JSON（thought/action/actionInput/finalAnswer），
     * 但模型是"文本生成器"，输出未必是标准 JSON——它可能用 Markdown 代码块 ```json ... ``` 把 JSON 包起来，
     * 也可能顺便带点解释文字。所以这里要容错：先抽取纯 JSON，再解析；解析失败就退化为"直接把原文当回答"，不让对话崩。</p>
     */
    private AgentDecision parseDecision(String text) {
        if (text == null || text.isBlank()) {
            // 模型啥也没回：给一个友好兜底，而不是抛异常吓用户。
            return new AgentDecision("", "", Map.of(), "抱歉，我没有理解您的问题，请换个说法或转人工客服。");
        }
        // 先尽量抽出干净的 JSON（见 extractJson 注释：处理 Markdown 包裹）。
        String json = extractJson(text);
        try {
            // Jackson 把 JSON 字符串解析成树节点，再按字段名取出来填进决策对象。
            JsonNode node = objectMapper.readTree(json);
            AgentDecision decision = new AgentDecision();
            // path(...).asText("")：字段缺失时返回空串而非抛异常，保证健壮性。
            decision.setThought(node.path("thought").asText(""));
            decision.setAction(node.path("action").asText(""));
            // actionInput 是嵌套对象（工具参数），需要转成 Map。先判空，避免把 JSON 的 null 塞进 Map。
            if (node.has("actionInput") && !node.get("actionInput").isNull()) {
                decision.setActionInput(objectMapper.convertValue(node.get("actionInput"),
                        new TypeReference<Map<String, Object>>() {
                        }));
            } else {
                decision.setActionInput(Map.of());
            }
            decision.setFinalAnswer(node.path("finalAnswer").asText(""));
            return decision;
        } catch (Exception e) {
            // JSON 实在解析不出（模型自由发挥了一段话）：不当作错误，直接把原文作为最终回答返回，体验更顺。
            log.warn("[Agent] 决策 JSON 解析失败，视为直接回答。原文：{}", text);
            return new AgentDecision("", "", Map.of(), text);
        }
    }

    /**
     * 从模型输出里抽取 JSON 子串。
     * <p><b>为什么</b>：大模型常把 JSON 用 Markdown 代码块 ```json ... ``` 包裹，或直接混在解释文字里。
     * 这里做两件事：① 剥掉 ``` 包裹；② 截出第一个 { 到最后一个 } 之间的内容。
     * 不这么做，Jackson 解析"带 markdown 的 JSON"会直接报错，整轮对话就废了。</p>
     */
    private String extractJson(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int first = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (first > 0 && last > first) {
                t = t.substring(first + 1, last).trim();
            }
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }

    /**
     * 把字符串转义成安全的 JSON 字符串片段，用于拼进事件 payload。
     * <p><b>为什么</b>：前端事件里我们手拼 JSON（如 {"tool":"x","description":"..."}）。
     * 如果 description 本身含双引号或反斜杠（比如工具描述里有引号），不转义会让 JSON 断掉、前端解析失败。
     * 这里把 \ 和 " 都加上反斜杠前缀，保证拼出来的 JSON 合法。</p>
     */
    private String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
