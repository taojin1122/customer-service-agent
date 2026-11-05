package com.csagent.agent.service;

import com.csagent.agent.engine.AgentEngine;
import com.csagent.agent.event.AgentEvent;
import com.csagent.agent.model.ChatRequest;
import com.csagent.common.context.CurrentUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 智能客服对话门面服务。
 *
 * <p><b>现在它只剩"转发"，为什么还留着</b>：它是 Web 层（{@code ChatController}）与引擎之间唯一的接缝。
 * 将来要按意图分流到不同 Agent、按租户切换引擎实现、或加限流/灰度时，改动落在这里，
 * 不用去动 {@code AgentEngine} 和所有调用方。</p>
 *
 * <p><b>刻意不在这里做的事（历史上做错过，见下）</b>：
 * 提示注入检测、意图识别、指标埋点都只在引擎内执行一次。本类曾各自再跑一遍，
 * 造成①同一句话被安全过滤两次；②意图识别跑两次（同一请求两次结果可能不一致）；
 * ③前端收到两条 {@code intent} 事件；④指标被记两份。</p>
 *
 * <p>编排链路：门面 → {@code AgentEngine}（安全闸门 → 意图识别 → ReAct 循环 → 输出泄露检测 → 指标）。</p>
 */
@Service
public class ChatAgentService {

    private final AgentEngine agentEngine;

    public ChatAgentService(AgentEngine agentEngine) {
        this.agentEngine = agentEngine;
    }

    /**
     * 流式对话：直接交给引擎执行，事件流原样透传。
     *
     * <p>安全检测与意图识别由引擎在最前面统一处理（被拦截时引擎会给出固定安全话术并结束流），
     * 门面不做任何二次校验，避免重复执行与事件重复推送。</p>
     *
     * <p><b>身份为什么由调用方传入</b>：本方法可能运行在 SSE 的异步线程上，此时
     * {@code TenantContext}（ThreadLocal）已失效。在 Web 层取出 {@link CurrentUser} 再传下来，
     * 身份数据就是普通对象，跨线程不丢。</p>
     */
    public Flux<AgentEvent> streamChat(ChatRequest request, CurrentUser user) {
        return agentEngine.stream(request, user);
    }

    /**
     * 同步对话（内部测试 / 简单调用）：把流式回答拼成完整文本。
     *
     * <p>只取 {@code answer} 事件：{@code think}/{@code tool_call} 等过程事件对调用方无意义。</p>
     */
    public Mono<String> syncChat(ChatRequest request, CurrentUser user) {
        return streamChat(request, user)
                .filter(e -> "answer".equals(e.getType()))
                .map(AgentEvent::getData)
                .reduce("", (a, b) -> a + b)
                .timeout(Duration.ofSeconds(60));
    }
}
