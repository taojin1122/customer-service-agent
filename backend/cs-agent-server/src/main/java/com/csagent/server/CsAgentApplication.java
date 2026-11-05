package com.csagent.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Boot 4 重组了 autoconfigure 包结构，@EntityScan 从 autoconfigure.domain 迁到 persistence.autoconfigure
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 智能客服 Agent 启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.csagent")
// 显式列出实体与仓储所在包：Boot 默认只扫描启动类所在包（com.csagent.server），
// 业务模块在 com.csagent.memory / com.csagent.auth 下，不声明就找不到，
// 表现为启动成功但 Repository 注入失败。新增模块时必须同步加到这里。
@EntityScan(basePackages = {"com.csagent.memory.entity", "com.csagent.auth.entity"})
@EnableJpaRepositories(basePackages = {"com.csagent.memory.repository", "com.csagent.auth.repository"})
public class CsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsAgentApplication.class, args);
    }
}
