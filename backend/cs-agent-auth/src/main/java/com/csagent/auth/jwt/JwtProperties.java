package com.csagent.auth.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项（前缀 {@code cs-agent.auth.jwt}）。
 *
 * <p><b>secret 的取值纪律</b>：生产环境必须通过环境变量 {@code CS_AGENT_JWT_SECRET} 注入，
 * 长度不短于 32 字节（HS256 的硬性要求）。写在 {@code application.yml} 里的默认值只能用于本地开发——
 * 密钥一旦进版本库，等于任何人都能伪造任意租户的令牌（AGENTS.md §2.2）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "cs-agent.auth.jwt")
public class JwtProperties {

    /**
     * 签名密钥。默认值仅限本地开发，生产必须覆盖。
     */
    private String secret = "cs-agent-dev-only-secret-change-me-32bytes";

    /**
     * 令牌有效期（分钟）。默认 12 小时：覆盖一个工作班次，坐席中途不会被踢下线。
     * 不做滑窗续期是有意的——当前没有刷新令牌机制，续期会让"停用账号"无法及时生效。
     */
    private long expireMinutes = 720;
}
