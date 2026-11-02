package com.csagent.prompt.example;

import lombok.Data;

/**
 * Few-shot 示例（少样本提示）：一组问答对，引导模型模仿回答风格。
 */
@Data
public class FewShotExample {

    private String user;

    private String assistant;

    public FewShotExample(String user, String assistant) {
        this.user = user;
        this.assistant = assistant;
    }
}
