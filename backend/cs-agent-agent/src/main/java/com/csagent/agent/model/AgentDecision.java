package com.csagent.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * LLM 决策输出模型（ReAct 循环里，每一轮让模型"填表"返回的结构）。
 *
 * <p><b>为什么需要这个模型</b>：我们不让模型自由发挥一大段话，而是要求它按固定 JSON 格式回答，
 * 这样程序才能机器可读地判断"它想干嘛"。这个类的 4 个字段就是那张表的字段。模型每轮二选一：</p>
 * <ul>
 *   <li><b>调用工具</b>：填 action（工具名）+ actionInput（参数），thought 写它为什么这么干</li>
 *   <li><b>直接回答</b>：action 留空，finalAnswer 写给用户的最终话</li>
 * </ul>
 * <p>引擎（AgentEngine.parseDecision）负责把这个 JSON 解析成本对象，再决定走"工具分支"还是"回答分支"。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDecision {

    /** 模型的"内心独白"：它这一步在想什么。不驱动逻辑，但推给前端展示，让用户感觉机器人在认真推理。 */
    private String thought;

    /** 要调用的工具名。为空（或空白）即表示"不再调工具，直接回答"。这是引擎区分两种分支的判断依据。 */
    private String action;

    /** 工具参数（JSON 对象）。对应 action 传入的具体值，如 {"roomNo":"3-2-501"}。 */
    private Map<String, Object> actionInput;

    /** 最终回答文本。仅当 action 为空时才有意义，是推给用户的话。 */
    private String finalAnswer;
}
