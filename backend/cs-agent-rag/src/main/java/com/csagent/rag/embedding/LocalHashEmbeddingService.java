package com.csagent.rag.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地哈希 Embedding 实现（零外部依赖，始终注册为兜底）。
 * <p>
 * 原理：基于字符 n-gram 的哈希向量化——把文本拆成字/词片段，
 * 每个片段哈希到向量维度上累加，得到固定维度的稀疏向量。
 * 用于 demo 环境开箱即用；配置了外部 Embedding API 时由 @Primary 覆盖。
 */
@Slf4j
@Component
public class LocalHashEmbeddingService implements EmbeddingService {

    /** 向量维度（固定 256 维） */
    private static final int DIM = 256;

    @Override
    public float[] embed(String text) {
        float[] vec = new float[DIM];
        if (text == null || text.isBlank()) {
            return vec;
        }
        String normalized = text.toLowerCase();
        // 字符 unigram + bigram
        List<String> grams = new ArrayList<>();
        for (int i = 0; i < normalized.length(); i++) {
            grams.add(String.valueOf(normalized.charAt(i)));
        }
        for (int i = 0; i < normalized.length() - 1; i++) {
            grams.add(normalized.substring(i, i + 2));
        }
        for (String g : grams) {
            int h = Math.abs(hash(g));
            int idx = h % DIM;
            // 符号由哈希位决定，降低碰撞方向抵消
            vec[idx] += ((h >>> 16) & 1) == 0 ? 1.0f : -1.0f;
        }
        // L2 归一化，便于余弦相似度计算
        float norm = 0f;
        for (float v : vec) {
            norm += v * v;
        }
        if (norm > 0) {
            float sqrt = (float) Math.sqrt(norm);
            for (int i = 0; i < DIM; i++) {
                vec[i] /= sqrt;
            }
        }
        return vec;
    }

    private int hash(String s) {
        int h = 0;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h = 31 * h + b;
        }
        return h;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (String t : texts) {
            result.add(embed(t));
        }
        return result;
    }

    @Override
    public int dimension() {
        return DIM;
    }

    @Override
    public String providerName() {
        return "LocalHashEmbedding(本地哈希，demo)";
    }
}
