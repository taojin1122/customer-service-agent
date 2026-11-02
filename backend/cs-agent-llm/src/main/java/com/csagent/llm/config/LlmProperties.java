package com.csagent.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 多模型配置。
 * <p>
 * 通过 cs-agent.llm 前缀配置，支持同时配置多个模型（DeepSeek / OpenAI / 其他 OpenAI 兼容服务），
 * 运行时按 id 路由切换，演示多模型接入能力。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cs-agent.llm")
public class LlmProperties {

    /** 默认模型 id */
    private String defaultModel = "deepseek";

    /** 多模型列表 */
    private List<ModelConfig> models = new ArrayList<>();

    @Data
    public static class ModelConfig {

        /** 模型唯一标识，如 deepseek / openai / qwen */
        private String id;

        /** 展示名称 */
        private String name;

        /** OpenAI 兼容的 base-url */
        private String baseUrl;

        /** API Key */
        private String apiKey;

        /** 模型名，如 deepseek-chat / gpt-4o-mini */
        private String model;

        /** 采样温度 */
        private Double temperature = 0.7;

        /** 最大输出 token */
        private Integer maxTokens = 2048;
    }
}
