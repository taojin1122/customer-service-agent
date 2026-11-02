package com.csagent.tool.tools;

import com.csagent.tool.annotation.AgentTool;
import com.csagent.tool.api.AgentToolProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物业费账单查询工具（演示数据，内存实现）。
 */
@Component
public class BillTools implements AgentToolProvider {

    /** 房号 -> 账单信息 */
    private final Map<String, String> bills = new ConcurrentHashMap<>();

    public BillTools() {
        bills.put("3-2-501", "2025年1月物业费 356.00 元，已缴清");
        bills.put("5-1-1202", "2025年1月物业费 482.00 元，未缴纳；2024年12月水费 68.50 元，未缴纳");
        bills.put("1-3-301", "2025年1月物业费 289.00 元，已缴清");
    }

    /**
     * 按房号查询物业费账单
     */
    @AgentTool(description = "按房号（格式：楼栋-单元-房号，如 3-2-501）查询物业费、水电费账单及缴纳状态。用于回答'我家物业费多少''欠费了吗'等问题", domain = "billing")
    public String query_bill(String roomNo) {
        String bill = bills.get(roomNo);
        if (bill == null) {
            return "未查询到房号【" + roomNo + "】的账单信息，请确认房号格式是否正确（如 3-2-501）";
        }
        return "房号 " + roomNo + " 的账单：" + bill;
    }
}
