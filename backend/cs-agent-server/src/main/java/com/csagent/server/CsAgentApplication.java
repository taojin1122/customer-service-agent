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
@EntityScan(basePackages = "com.csagent.memory.entity")
@EnableJpaRepositories(basePackages = "com.csagent.memory.repository")
public class CsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsAgentApplication.class, args);
    }
}
