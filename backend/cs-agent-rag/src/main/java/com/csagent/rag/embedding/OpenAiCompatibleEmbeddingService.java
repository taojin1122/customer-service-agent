package com.csagent.rag.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI 兼容 Embedding API 实现（可选）。
 * <p>
 * 配置了 cs-agent.llm.embedding.base-url 与 api-key 时启用（如硅基流动 SiliconFlow 的 bge-m3、
 * OpenAI text-embedding-3-small 等）。未配置时自动回退到本地哈希实现。
 */
@Slf4j
@Component
@Primary
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${cs-agent.llm.embedding.base-url:}') && T(org.springframework.util.StringUtils).hasText('${cs-agent.llm.embedding.api-key:}')")
public class OpenAiCompatibleEmbeddingService implements EmbeddingService {

    private final OpenAiEmbeddingModel embeddingModel;
    /** 复用同一份 Options，避免每次调用都重建（2.0 里 baseUrl / apiKey 也挂在 Options 上） */
    private final OpenAiEmbeddingOptions embeddingOptions;
    private final String model;

    public OpenAiCompatibleEmbeddingService(
            @Value("${cs-agent.llm.embedding.base-url}") String baseUrl,
            @Value("${cs-agent.llm.embedding.api-key}") String apiKey,
            @Value("${cs-agent.llm.embedding.model}") String model) {
        this.model = model;
        // Spring AI 2.0 起底层换成官方 openai-java SDK：不再有 OpenAiApi 中间对象，
        // baseUrl / apiKey 直接写在 Options 上，build() 时框架据此创建底层客户端。
        this.embeddingOptions = OpenAiEmbeddingOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .build();
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .metadataMode(MetadataMode.EMBED)
                .options(embeddingOptions)
                .build();
        log.info("启用外部 Embedding API：model={}, baseUrl={}", model, baseUrl);
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), OpenAiEmbeddingOptions.builder().model(model).build()));
        if (response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalStateException("Embedding API 无返回结果");
        }
        return response.getResults().get(0).getOutput();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(texts, OpenAiEmbeddingOptions.builder().model(model).build()));
        return response.getResults().stream().map(r -> r.getOutput()).toList();
    }

    @Override
    public int dimension() {
        return 1536;
    }

    @Override
    public String providerName() {
        return "OpenAI 兼容 Embedding API (" + model + ")";
    }
}
