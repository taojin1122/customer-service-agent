package com.csagent.tool.tools;

import com.csagent.tool.annotation.AgentTool;
import com.csagent.tool.api.AgentToolProvider;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 客服辅助工具：营业时间查询、常见咨询。
 */
@Component
public class ServiceInfoTools implements AgentToolProvider {

    /**
     * 查询客服与物业服务中心工作时间
     */
    @AgentTool(description = "查询客服中心、物业服务中心的营业时间与服务电话。用于回答'几点上班''周末营业吗''怎么联系物业'等问题", domain = "service")
    public String query_service_hours() {
        return "客服中心工作时间：每日 8:00-22:00；物业服务中心：周一至周日 8:30-17:30。服务热线：400-888-0000。";
    }

    /**
     * 查询当前服务状态（演示上下文感知）
     */
    @AgentTool(description = "查询当前时间点客服是否在线。当用户表示需要人工服务时调用，判断是否可转人工", domain = "service")
    public String is_online_now() {
        LocalTime now = LocalTime.now();
        boolean online = !now.isBefore(LocalTime.of(8, 0)) && !now.isAfter(LocalTime.of(22, 0));
        return online
                ? "当前时间为 " + now + "，客服在线，可为您转接人工服务"
                : "当前时间为 " + now + "，已过人工服务时间（8:00-22:00），建议您留言或使用在线报修";
    }
}
