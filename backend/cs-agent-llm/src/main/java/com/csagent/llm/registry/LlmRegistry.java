package com.csagent.llm.registry;

import com.csagent.common.exception.BusinessException;
import com.csagent.llm.config.LlmProperties;
import com.csagent.llm.model.ModelInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多模型注册中心。
 * <p>
 * 根据 {@link LlmProperties} 配置动态构建多个 ChatModel / EmbeddingModel，
 * 以 id 为键注册，支持运行时路由切换。演示「多模型接入与切换」的 Agent 工程实践。
 */
@Slf4j
@Component
public class LlmRegistry {

    private final LlmProperties properties;

    /** 模型 id -> ChatModel */
    private final Map<String, ChatModel> chatModels = new LinkedHashMap<>();
    /** 模型 id -> StreamingChatModel */
    private final Map<String, StreamingChatModel> streamingChatModels = new LinkedHashMap<>();
    /** 模型 id -> EmbeddingModel */
    private final Map<String, EmbeddingModel> embeddingModels = new LinkedHashMap<>();

    public LlmRegistry(LlmProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.getModels() == null || properties.getModels().isEmpty()) {
            log.warn("未配置任何 LLM 模型（cs-agent.llm.models 为空），Agent 对话功能将不可用");
            return;
        }
        for (LlmProperties.ModelConfig cfg : properties.getModels()) {
            register(cfg);
        }
        log.info("LLM 模型注册完成：{} 个模型，默认：{}", chatModels.size(), properties.getDefaultModel());
    }

    private void register(LlmProperties.ModelConfig cfg) {
        if (!StringUtils.hasText(cfg.getId()) || !StringUtils.hasText(cfg.getBaseUrl())) {
            log.warn("跳过无效模型配置：id={}, baseUrl={}", cfg.getId(), cfg.getBaseUrl());
            return;
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey() == null ? "" : cfg.getApiKey())
                .build();

        // ChatModel（同步 + 流式一体）
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                //  模型名，如 deepseek-chat / gpt-4o-mini
                .model(cfg.getModel())
                // 采样温度
                .temperature(cfg.getTemperature())
                // 最大输出 token
                .maxTokens(cfg.getMaxTokens())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(chatOptions)
                .build();
        chatModels.put(cfg.getId(), chatModel);
        streamingChatModels.put(cfg.getId(), chatModel);
        log.info("注册对话模型：id={}, name={}, model={}, baseUrl={}", cfg.getId(), cfg.getName(), cfg.getModel(), cfg.getBaseUrl());
    }

    /**
     * 注册 Embedding 模型（单独配置，因 DeepSeek 官方暂无 Embedding API）
     */
    public void registerEmbedding(String id, String baseUrl, String apiKey, String model) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey == null ? "" : apiKey)
                .build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build());
        embeddingModels.put(id, embeddingModel);
        log.info("注册 Embedding 模型：id={}, model={}, baseUrl={}", id, model, baseUrl);
    }

    /**
     * 获取指定 id 的 ChatModel
     */
    public ChatModel getChatModel(String modelId) {
        String id = StringUtils.hasText(modelId) ? modelId : properties.getDefaultModel();
        ChatModel model = chatModels.get(id);
        if (model == null) {
            throw new BusinessException("模型未注册：" + id + "，请检查 cs-agent.llm.models 配置");
        }
        return model;
    }

    /**
     * 获取指定 id 的 StreamingChatModel
     */
    public StreamingChatModel getStreamingChatModel(String modelId) {
        String id = StringUtils.hasText(modelId) ? modelId : properties.getDefaultModel();
        StreamingChatModel model = streamingChatModels.get(id);
        if (model == null) {
            throw new BusinessException("模型未注册：" + id + "，请检查 cs-agent.llm.models 配置");
        }
        return model;
    }

    /**
     * 获取指定 id 的 EmbeddingModel
     */
    public EmbeddingModel getEmbeddingModel(String modelId) {
        EmbeddingModel model = embeddingModels.get(modelId);
        if (model == null) {
            throw new BusinessException("Embedding 模型未注册：" + modelId);
        }
        return model;
    }

    /**
     * 是否已注册任何对话模型
     */
    public boolean hasChatModel() {
        return !chatModels.isEmpty();
    }

    /**
     * 模型列表（用于前端展示与切换）
     */
    public List<ModelInfo> listModels() {
        List<ModelInfo> list = new ArrayList<>();
        if (properties.getModels() != null) {
            for (LlmProperties.ModelConfig cfg : properties.getModels()) {
                list.add(new ModelInfo(cfg.getId(), cfg.getName(), cfg.getModel(),
                        cfg.getId().equals(properties.getDefaultModel())));
            }
        }
        return list;
    }
}
