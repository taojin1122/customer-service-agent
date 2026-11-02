package com.csagent.llm.service;

import com.csagent.common.exception.BusinessException;
import com.csagent.llm.registry.LlmRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LLM 对话服务封装。
 * <p>
 * 统一封装「同步对话」与「流式对话」，屏蔽底层 ChatModel 差异；
 * 支持按模型 id 路由，便于多模型切换。
 */
@Slf4j
@Service
public class ChatService {

    private final LlmRegistry llmRegistry;

    public ChatService(LlmRegistry llmRegistry) {
        this.llmRegistry = llmRegistry;
    }

    /**
     * 同步对话：仅一个用户消息 + 可选系统提示词
     */
    public String chat(String userContent, String systemPrompt, String modelId) {
        if (!llmRegistry.hasChatModel()) {
            throw new BusinessException("未配置 LLM 模型，请检查 application.yml 中 cs-agent.llm.models 配置");
        }
        Prompt prompt = buildPrompt(systemPrompt, userContent);
        ChatResponse response = llmRegistry.getChatModel(modelId).call(prompt);
        return response.getResult() != null ? response.getResult().getOutput().getText() : "";
    }

    /**
     * 同步对话：传入完整消息列表（支持多轮记忆 + 工具结果回填）
     */
    public ChatResponse chat(List<Message> messages, String modelId) {
        if (!llmRegistry.hasChatModel()) {
            throw new BusinessException("未配置 LLM 模型，请检查 application.yml 中 cs-agent.llm.models 配置");
        }
        return llmRegistry.getChatModel(modelId).call(new Prompt(messages));
    }

    /**
     * 流式对话：传入完整消息列表（支持多轮记忆 + 工具结果回填）
     */
    public Flux<ChatResponse> stream(List<Message> messages, String modelId) {
        if (!llmRegistry.hasChatModel()) {
            return Flux.error(new BusinessException("未配置 LLM 模型，请检查 application.yml 中 cs-agent.llm.models 配置"));
        }
        StreamingChatModel model = llmRegistry.getStreamingChatModel(modelId);
        return model.stream(new Prompt(messages));
    }

    private Prompt buildPrompt(String systemPrompt, String userContent) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userContent)
            ));
        }
        return new Prompt(new UserMessage(userContent));
    }
}
