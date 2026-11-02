package com.csagent.agent.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 运行事件：贯穿一次对话的完整执行轨迹，通过 SSE 推送给前端。
 * <p>
 * 事件类型：
 * - intent     ：意图识别结果
 * - think      ：LLM 推理（思考过程）
 * - tool_call  ：决定调用工具（含参数）
 * - tool_result：工具执行结果
 * - rag        ：知识库检索结果
 * - answer     ：最终回答（流式文本增量）
 * - done       ：本次对话结束
 * - error      ：错误
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {

    /** 事件类型 */
    private String type;

    /** 事件数据（JSON 字符串或文本） */
    private String data;

    public static AgentEvent of(String type, String data) {
        return new AgentEvent(type, data);
    }
}
