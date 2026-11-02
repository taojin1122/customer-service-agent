package com.csagent.prompt.builder;

import com.csagent.prompt.template.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统提示词构建器：面向「智能客服」角色设定。
 * <p>
 * 演示 Prompt 工程的几个核心手法：
 * 1. 角色设定（Role Prompting）：你是谁、语气、边界
 * 2. 任务约束：只能做什么、禁止做什么
 * 3. 输出规范：结构化要求
 * 4. 动态注入：当前时间、可用工具清单、知识库检索结果由外部注入
 */
@Component
public class SystemPromptBuilder {

    private final PromptTemplate promptTemplate;

    public SystemPromptBuilder(PromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    /**
     * 构建基础系统提示词（角色 + 约束）
     */
    public String buildBase(String agentName, String companyName) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("agentName", agentName);
        vars.put("companyName", companyName);
        return promptTemplate.render("system-base", vars);
    }

    /**
     * 构建带工具清单的系统提示词
     */
    public String buildWithTools(String agentName, String companyName, String toolDescriptions) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("agentName", agentName);
        vars.put("companyName", companyName);
        vars.put("tools", toolDescriptions);
        return promptTemplate.render("system-with-tools", vars);
    }

    /**
     * 构建带知识库上下文的系统提示词（RAG 场景）
     */
    public String buildWithContext(String agentName, String companyName, String knowledgeContext) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("agentName", agentName);
        vars.put("companyName", companyName);
        vars.put("knowledge", knowledgeContext);
        return promptTemplate.render("system-with-knowledge", vars);
    }
}
