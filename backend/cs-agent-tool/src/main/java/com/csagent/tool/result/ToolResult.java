package com.csagent.tool.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 工具名 */
    private String toolName;

    /** 执行是否成功 */
    private boolean success;

    /** 结果内容（文本形式，回填给 LLM 作为观察结果） */
    private String output;

    /** 错误信息 */
    private String error;

    public static ToolResult ok(String toolName, String output) {
        return new ToolResult(toolName, true, output, null);
    }

    public static ToolResult fail(String toolName, String error) {
        return new ToolResult(toolName, false, null, error);
    }
}
