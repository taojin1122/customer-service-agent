package com.csagent.agent.intent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图识别器：基于"规则 + 关键词"的轻量实现（演示用）。
 *
 * <p><b>它解决什么问题</b>：用户一句话进来，机器人得先判断"他到底想干嘛"——
 * 是查报修进度？是账单问题？还是单纯打招呼？这叫"意图识别"。
 * 识别出来后，Agent 才能决定要不要调工具、调哪个工具，或者干脆走闲聊。</p>
 *
 * <p><b>为什么这里用"规则"而不是大模型</b>：生产里意图识别通常由 LLM 做 Few-shot 分类，或训个专用小模型，
 * 覆盖长尾说法更全。但本项目先用规则版，好处是<b>零延迟、零调用成本、结果完全可解释</b>——
 * 你能精确知道为什么判成"报修"。规则版的缺点是漏判多（用户换个说法就认不出）。
 * 它在本项目里定位成 Agent 的"前置路由"：先用规则做粗分（业务意图 vs 闲聊），再交给主引擎细处理。</p>
 */
@Slf4j
@Component
public class IntentClassifier {

    /**
     * 房号提取正则：匹配物业场景常见的"3-2-501 / 3栋2单元501 / 3幢2单元501"等写法。
     * <p><b>为什么单独抽房号</b>：客服里"报修""查账单"几乎都要绑定具体房号才能查数据。
     * 提前用正则把房号抠出来，后面调工具时直接当参数用，不用让模型再去猜。正则比模型稳定、便宜。</p>
     */
    private static final Pattern ROOM_PATTERN = Pattern.compile(
            "(\\d{1,2})[栋幢]?[-\\s]*(\\d{1,2})[单元]?[-\\s]*(\\d{1,4})");

    /**
     * 意图关键词字典：key=意图类型，value=命中即算该类的关键词数组。
     * <p>为什么拆这么细：每种业务（报修/账单/服务时间/转人工/问候）对应不同的后续动作，
     * 分得越清，引擎路由越准。注意 "repair_create" 和 "repair_query" 都含"报修"——
     * 前者是"要发起报修"（含漏水/跳闸等故障词），后者是"查已有工单进度"，靠额外关键词区分。</p>
     */
    private static final Map<String, String[]> KEYWORDS = Map.of(
            "repair_query", new String[]{"报修", "工单", "维修单", "修好了", "修到哪"},
            "repair_create", new String[]{"漏水", "渗水", "跳闸", "断电", "停电", "门锁", "坏了", "堵", "报修"},
            "bill_query", new String[]{"物业费", "账单", "欠费", "水费", "电费", "多少钱", "缴费", "缴纳"},
            "service_hours", new String[]{"几点上班", "营业时间", "工作时间", "几点下班", "周末", "电话", "联系"},
            "human_service", new String[]{"人工", "转人工", "客服电话", "投诉"},
            "greeting", new String[]{"你好", "您好", "hello", "hi", "在吗", "早上好", "下午好"}
    );

    /**
     * 识别一句话的意图。
     * <p>算法（简单但够用）：遍历每个意图的关键词，统计这句话命中了几个词，
     * 命中比例最高的意图就判为最终意图；默认兜底为 "chat"（闲聊）。</p>
     */
    public IntentResult classify(String message) {
        String text = message == null ? "" : message.trim();
        // 空消息没法判断，直接归 unknown，让引擎走兜底或追问。
        if (text.isEmpty()) {
            return IntentResult.of("unknown", "空消息", 0);
        }

        // 规则匹配：按"命中关键词数 / 该意图总关键词数"算命中率，取最高者为最佳意图。
        // 为什么用比例而不是绝对命中数：不同意图关键词数量不同（有的 3 个有的 8 个），
        // 用比例才能公平比较，避免"关键词多但只命中 1 个"的意图反而压过"关键词少但全命中"的意图。
        String bestType = "chat";
        double bestScore = 0;
        for (Map.Entry<String, String[]> e : KEYWORDS.entrySet()) {
            int hit = 0;
            for (String kw : e.getValue()) {
                if (text.contains(kw)) {
                    hit++;
                }
            }
            double score = (double) hit / e.getValue().length;
            if (score > bestScore) {
                bestScore = score;
                bestType = e.getKey();
            }
        }

        // 把命中率换算成一个 0.5~0.9 的"置信度"分数，方便上层判断靠不靠谱。
        // 为什么下限 0.5：只要命中了该意图任一关键词，就给个及格分，表示"至少有点谱"；
        // 再叠加命中比例*0.4，命中越多分越高，但封顶 0.9（规则版不可全信，留余地给更准的模型版）。
        IntentResult result = IntentResult.of(bestType,
                describe(bestType), Math.min(0.9, 0.5 + bestScore * 0.4));

        // 顺手抽取房号实体，塞进 entities，供后续工具调用直接使用（如查某房号的账单/报修）。
        Matcher m = ROOM_PATTERN.matcher(text);
        if (m.find()) {
            result.getEntities().put("roomNo", m.group(1) + "-" + m.group(2) + "-" + m.group(3));
        }
        return result;
    }

    /** 把意图类型翻译成给人看的中文描述（用于日志/前端展示，也便于调试时一眼看懂判了啥）。 */
    private String describe(String type) {
        return switch (type) {
            case "repair_query" -> "查询报修进度";
            case "repair_create" -> "发起报修";
            case "bill_query" -> "账单查询";
            case "service_hours" -> "服务时间咨询";
            case "human_service" -> "转人工服务";
            case "greeting" -> "打招呼";
            default -> "闲聊";
        };
    }
}
