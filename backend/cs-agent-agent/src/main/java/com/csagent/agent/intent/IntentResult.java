package com.csagent.agent.intent;

import lombok.Data;

/**
 * 意图识别结果。
 */
@Data
public class IntentResult {

    /** 意图类别 */
    private String type;

    /** 意图说明 */
    private String description;

    /** 置信度 0-1 */
    private double confidence;

    /** 提取的实体（如房号、业主名） */
    private java.util.Map<String, String> entities;

    public static IntentResult of(String type, String description, double confidence) {
        IntentResult r = new IntentResult();
        r.type = type;
        r.description = description;
        r.confidence = confidence;
        r.entities = new java.util.HashMap<>();
        return r;
    }
}
