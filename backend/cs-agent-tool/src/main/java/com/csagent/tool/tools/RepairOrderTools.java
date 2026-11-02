package com.csagent.tool.tools;

import com.csagent.tool.annotation.AgentTool;
import com.csagent.tool.api.AgentToolProvider;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 报修工单业务工具（演示数据，内存实现）。
 * <p>
 * 演示 Tool Calling 的核心场景：把已有业务方法暴露为 Agent 工具，
 * 让 LLM 理解用户意图后自动调用。真实项目中这里可直接调用你的 Service 方法。
 */
@Component
public class RepairOrderTools implements AgentToolProvider {

    /** 内存演示数据：工单号 -> 工单 */
    private final Map<String, RepairOrder> orders = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1000);

    public RepairOrderTools() {
        // 预置演示数据
        RepairOrder o1 = new RepairOrder("WO20250101001", "张先生", "3栋2单元501", "水管漏水", "处理中", "2025-01-05 09:12");
        RepairOrder o2 = new RepairOrder("WO20250102002", "李女士", "5栋1单元1202", "电路跳闸", "待分配", "2025-01-06 14:30");
        RepairOrder o3 = new RepairOrder("WO20250103003", "王阿姨", "1栋3单元301", "门锁损坏", "已完成", "2025-01-03 10:05");
        orders.put(o1.getOrderNo(), o1);
        orders.put(o2.getOrderNo(), o2);
        orders.put(o3.getOrderNo(), o3);
    }

    /**
     * 按业主姓名查询报修工单
     */
    @AgentTool(description = "按业主姓名查询报修工单列表，返回工单号、地址、问题描述、状态。用于回答'我的报修单怎么样了'之类的问题", domain = "repair")
    public String query_repair_order(String ownerName) {
        List<RepairOrder> matched = new ArrayList<>();
        for (RepairOrder o : orders.values()) {
            if (o.getOwnerName().contains(ownerName)) {
                matched.add(o);
            }
        }
        if (matched.isEmpty()) {
            return "未查询到业主【" + ownerName + "】的报修工单";
        }
        StringBuilder sb = new StringBuilder("查询到 " + matched.size() + " 条报修工单：\n");
        for (RepairOrder o : matched) {
            sb.append("- 工单号：").append(o.getOrderNo())
                    .append("，地址：").append(o.getAddress())
                    .append("，问题：").append(o.getDescription())
                    .append("，状态：").append(o.getStatus())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * 创建报修工单（高权限操作，演示安全确认）
     */
    @AgentTool(description = "创建新的报修工单。当用户报修漏水、断电、门锁等问题时调用。参数 ownerName 为业主姓名，address 为详细地址，description 为问题描述", domain = "repair", highRisk = true)
    public String create_repair_order(String ownerName, String address, String description) {
        String orderNo = "WO" + System.currentTimeMillis() + seq.incrementAndGet();
        RepairOrder o = new RepairOrder(orderNo, ownerName, address, description, "待分配",
                LocalDateTime.now().toString().replace("T", " ").substring(0, 16));
        orders.put(orderNo, o);
        return "报修工单创建成功！工单号：" + orderNo + "，我们已安排工作人员处理，请保持电话畅通。";
    }

    @Data
    public static class RepairOrder {
        private String orderNo;
        private String ownerName;
        private String address;
        private String description;
        private String status;
        private String createTime;

        public RepairOrder(String orderNo, String ownerName, String address, String description, String status, String createTime) {
            this.orderNo = orderNo;
            this.ownerName = ownerName;
            this.address = address;
            this.description = description;
            this.status = status;
            this.createTime = createTime;
        }
    }
}
