package com.csagent.prompt.example;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能客服 Few-shot 示例库。
 * <p>
 * 少样本提示（Few-shot Prompting）：给出少量高质量示例，让模型模仿
 * 回答的语气、结构与边界，比纯规则约束更稳定。
 */
@Component
public class FewShotProvider {

    /**
     * 客服基础示例：教会模型「简洁、礼貌、确认信息、不编造」的风格
     */
    public List<FewShotExample> baseExamples() {
        return List.of(
                new FewShotExample(
                        "你们物业费怎么交？",
                        "您好，物业费可以通过以下方式缴纳：1）物业前台现场缴纳；2）微信公众号在线缴纳；3）对公转账。请问您方便用哪种方式？我可以帮您查询具体金额。"
                ),
                new FewShotExample(
                        "我家水管漏水了怎么办？",
                        "很抱歉给您带来困扰！漏水问题建议尽快报修：请您告诉我具体的楼栋房号，我可以立即为您创建报修工单，并安排维修师傅尽快上门。"
                ),
                new FewShotExample(
                        "晚上几点前能联系到客服？",
                        "我们的客服时间为每日 8:00-22:00，其余时间可以通过在线报修或留言，我们会在次日第一时间处理。请问有什么可以帮您？"
                )
        );
    }
}
