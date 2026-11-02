-- =============================================
-- 智能客服 Agent 数据库初始化脚本（MySQL 生产形态）
-- 说明：默认使用 H2 时由 JPA 自动建表，无需执行本脚本。
-- 使用 MySQL 时建议先执行本脚本初始化库。
-- =============================================

CREATE DATABASE IF NOT EXISTS cs_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cs_agent;

-- 会话表
CREATE TABLE IF NOT EXISTS cs_conversation (
    id          VARCHAR(64)  NOT NULL COMMENT '会话ID',
    title       VARCHAR(200) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
    model_id    VARCHAR(64)  DEFAULT NULL COMMENT '使用的模型ID',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0/1',
    create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客服会话';

-- 消息表
CREATE TABLE IF NOT EXISTS cs_message (
    id              VARCHAR(64) NOT NULL COMMENT '消息ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    role            VARCHAR(20) NOT NULL COMMENT '角色 user/assistant/tool/system',
    content         TEXT        DEFAULT NULL COMMENT '消息内容',
    tool_calls      TEXT        DEFAULT NULL COMMENT '工具调用记录(JSON)',
    seq_no          BIGINT      NOT NULL DEFAULT 0 COMMENT '消息序号',
    create_time     DATETIME    DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_conversation (conversation_id, seq_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客服消息';
