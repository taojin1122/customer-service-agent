package com.csagent.tool.definition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具定义：Agent 工具的元数据描述。
 * <p>
 * Tool Calling 的核心概念：工具 = 「给 LLM 看的能力清单」+「可执行的代码」。
 * LLM 根据 name + description 决定是否调用、按 parameters 生成参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /** 工具名称（LLM 调用标识，须唯一，建议 verb_noun 格式） */
    private String name;

    /** 工具描述（LLM 判断何时调用的依据，要写清楚用途与适用场景） */
    private String description;

    /** 参数 JSON Schema（告诉 LLM 需要传哪些参数、什么类型、是否必填） */
    private Map<String, Object> parametersSchema;

    /** 是否高权限操作（如：开闸、退款，需要用户二次确认） */
    private boolean highRisk = false;

    /** 所属业务域 */
    private String domain;
}
