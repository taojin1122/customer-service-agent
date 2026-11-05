package com.csagent.auth.jwt;

import com.csagent.common.context.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 签发与校验。
 *
 * <p><b>为什么用 JWT 而不是服务端 session</b>：SSE 长连接 + 多实例部署下，session 需要粘性会话
 * 或集中式 session 存储；JWT 自带身份，任意实例都能独立校验，水平扩容无额外依赖。
 * 代价是"无法主动吊销"——见 {@link #parse} 的说明。</p>
 *
 * <p><b>令牌里放什么、不放什么</b>：只放租户、用户、角色这类"每次请求都要用、且不常变"的字段。
 * <b>不放密码、不放手机号等隐私</b>——JWT 只是 Base64 编码而非加密，任何人复制到
 * jwt.io 就能读到内容。</p>
 */
@Slf4j
@Service
public class JwtService {

    /**
     * claim 名用短写：令牌在每次 SSE 请求的 Header 里传输，claim 名过长会白占字节。
     * 但必须与 {@link #parse} 中的读取严格对应，改名等于让所有已签发令牌失效。
     */
    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_USERNAME = "unm";
    private static final String CLAIM_ROLE = "rol";
    private static final String CLAIM_PLATFORM = "plt";

    /** HS256 要求密钥不少于 256 bit，即 32 字节。低于此值 jjwt 会直接抛异常。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(JwtProperties properties) {
        byte[] raw = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        // 启动时就校验而不是等第一次登录报错：密钥配错属于部署事故，
        // 让它在启动阶段暴露，比上线后所有人登录失败要好。
        if (raw.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "cs-agent.auth.jwt.secret 长度不足 " + MIN_SECRET_BYTES + " 字节（HS256 要求），当前 "
                            + raw.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.expireMillis = properties.getExpireMinutes() * 60_000L;
    }

    /**
     * 签发令牌。
     *
     * <p>平台账号的 {@code tid} 写空值而不是省略该 claim：省略后解析时"字段缺失"和
     * "值为 null"无法区分，而这两者在排查越权问题时含义完全不同。</p>
     */
    public String issue(CurrentUser user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.userId())
                .claim(CLAIM_TENANT_ID, user.tenantId())
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_ROLE, user.role())
                .claim(CLAIM_PLATFORM, user.platform())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌，失败返回空。
     *
     * <p><b>为什么失败不抛异常</b>：调用方是过滤器，它只关心"能用 / 不能用"。
     * 把异常类型泄露到上层会让过滤器的控制流变成"用 catch 做分支"，容易漏掉某类异常导致 500。
     * 具体原因记在日志里。</p>
     *
     * <p><b>已知限制</b>：JWT 无状态，签发后无法主动吊销。账号被停用后，旧令牌在过期前仍然有效。
     * 当前靠较短有效期（12 小时）兜底；生产若要求"停用立即生效"，需引入令牌版本号
     * （用户表存 {@code token_version}，停用时自增，校验时比对）。</p>
     */
    public Optional<CurrentUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
            return Optional.of(new CurrentUser(
                    tenantId,
                    claims.getSubject(),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_ROLE, String.class),
                    Boolean.TRUE.equals(claims.get(CLAIM_PLATFORM, Boolean.class))
            ));
        } catch (ExpiredJwtException e) {
            // 过期是最常见的情况（用户挂机一晚上），用 debug 级别，避免日志被这类正常事件刷满
            log.debug("[鉴权] 令牌已过期：{}", e.getMessage());
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不匹配 / 格式错误：可能是伪造，也可能是前端传了脏数据，用 warn 便于排查攻击
            log.warn("[鉴权] 令牌校验失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 令牌有效期（秒），返回给前端用于提示"登录已过期"。
     */
    public long getExpireSeconds() {
        return expireMillis / 1000;
    }
}
