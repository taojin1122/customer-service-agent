package com.csagent.rag.vector;

/**
 * 向量计算工具。
 *
 * <p>抽出来的原因很实际：内存实现与数据库实现都要算余弦相似度，
 * 各写一份的后果不是"多几行代码"，而是两套实现对"维度不一致时返回什么"的处理分叉，
 * 检索结果就会随存储实现不同而不同——这种差异极难在测试里发现。</p>
 */
final class VectorMath {

    private VectorMath() {
    }

    /**
     * 余弦相似度，范围 [-1, 1]。
     *
     * <p>维度不一致或任一向量为零向量时返回 0（而不是抛异常）：
     * 检索是多文档场景，一条坏数据不该让整次检索失败。</p>
     */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
