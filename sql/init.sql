-- =============================================
-- 智能客服 Agent 数据库初始化脚本（MySQL 生产形态）
-- 说明：默认使用 H2 时由 JPA 自动建表，无需执行本脚本。
-- 使用 MySQL 时建议先执行本脚本初始化库。
-- =============================================

CREATE DATABASE IF NOT EXISTS cs_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cs_agent;

-- =============================================
-- 身份与租户（对应 cs-agent-auth 模块，AGENTS.md §9.5）
-- =============================================

-- 租户表：SaaS 模式下每家客户公司一条记录。
-- 必须最先存在，因为知识库/会话/工具订阅最终都要挂 tenant_id；
-- 后补字段容易，但"历史上混在一起的数据分不出归属"补不回来。
CREATE TABLE IF NOT EXISTS t_tenant (
    id          VARCHAR(64)  NOT NULL COMMENT '租户ID',
    code        VARCHAR(64)  NOT NULL COMMENT '租户编码：对外可读标识，登录与域名路由使用',
    name        VARCHAR(200) NOT NULL COMMENT '租户名称',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DISABLED，停用须在登录环节拦截',
    seat_quota  INT          DEFAULT NULL COMMENT '席位数上限，限制 AGENT 数量（收费抓手）',
    create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户';

-- 租户账号表：属于某个租户的登录用户，角色只有 ADMIN / AGENT。
-- 与 t_platform_user 分表的原因：两者的隔离要求相反，混表后任何一处漏写
-- WHERE tenant_id 都可能让租户账号读到平台账号（AGENTS.md §9.5）。
CREATE TABLE IF NOT EXISTS t_user (
    id            VARCHAR(64)  NOT NULL COMMENT '用户ID',
    tenant_id     VARCHAR(64)  NOT NULL COMMENT '所属租户ID，所有查询必须带上',
    username      VARCHAR(64)  NOT NULL COMMENT '登录名，全库唯一（登录只凭用户名+密码，无租户编码）',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希，禁止存明文或 MD5',
    role          VARCHAR(32)  NOT NULL COMMENT '角色 ADMIN / AGENT，用字符串便于新增角色',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DISABLED，离职置 DISABLED 不物理删除',
    create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户账号';

-- 平台账号表：我们自己的运营/运维，权限跨所有租户，没有 tenant_id。
CREATE TABLE IF NOT EXISTS t_platform_user (
    id            VARCHAR(64)  NOT NULL COMMENT '账号ID',
    username      VARCHAR(64)  NOT NULL COMMENT '登录名，全局唯一',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DISABLED',
    create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台账号';

-- =============================================
-- 会话与消息
-- =============================================

-- 会话表
-- tenant_id / user_id 是隔离的关键：租户管理员看本租户全部会话，坐席只看自己的。
-- 只按租户过滤会让坐席拉到同事与用户的对话，属于内部数据泄露（AGENTS.md §9.3）。
CREATE TABLE IF NOT EXISTS cs_conversation (
    id          VARCHAR(64)  NOT NULL COMMENT '会话ID',
    title       VARCHAR(200) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
    tenant_id   VARCHAR(64)  DEFAULT NULL COMMENT '所属租户ID，新数据必填',
    user_id     VARCHAR(64)  DEFAULT NULL COMMENT '会话归属的坐席账号',
    model_id    VARCHAR(64)  DEFAULT NULL COMMENT '使用的模型ID',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0/1',
    create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_conv_tenant (tenant_id, deleted, update_time),
    KEY idx_conv_tenant_user (tenant_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客服会话';

-- 消息表（不冗余存 tenant_id：归属校验统一走 cs_conversation，避免两处不一致）
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

-- =============================================
-- 存量库升级：登录方式从「租户编码 + 用户名」改为「用户名（全库唯一）+ 密码」
--
-- 执行前必须先处理重名：多个租户都有 admin 时，唯一索引会创建失败。
-- 先查出来再决定改哪个名字，不要直接删数据。
-- =============================================
-- SELECT username, COUNT(*) FROM t_user GROUP BY username HAVING COUNT(*) > 1;
--
-- ALTER TABLE t_user DROP INDEX uk_user_tenant_username;
-- ALTER TABLE t_user ADD UNIQUE KEY uk_user_username (username);
-- ALTER TABLE t_user ADD KEY idx_user_tenant (tenant_id);

-- =============================================
-- 存量库升级（已建过 cs_conversation 且没有新列的库执行以下语句）
-- 注意：新增列允许为 NULL 只为兼容改造前的历史数据。
-- 历史会话没有租户归属，任何租户都查不到它们——这是刻意的：
-- 宁可让旧数据不可见，也不能让它被任一租户读到（AGENTS.md §9.5）。
-- =============================================
-- ALTER TABLE cs_conversation
--     ADD COLUMN tenant_id VARCHAR(64) DEFAULT NULL COMMENT '所属租户ID' AFTER title,
--     ADD COLUMN user_id   VARCHAR(64) DEFAULT NULL COMMENT '会话归属的坐席账号' AFTER tenant_id,
--     ADD KEY idx_conv_tenant (tenant_id, deleted, update_time),
--     ADD KEY idx_conv_tenant_user (tenant_id, user_id);
