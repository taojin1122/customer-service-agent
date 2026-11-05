package com.csagent.auth.security;

import com.csagent.auth.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：默认拒绝，白名单放行。
 *
 * <p><b>为什么是"默认拒绝"而不是"默认放行"</b>：漏配一个受保护接口，默认拒绝的后果是
 * 该接口暂时不可用（功能问题，能立刻发现）；默认放行的后果是该接口裸奔（安全问题，
 * 可能几个月后才发现）。两者代价完全不对等。</p>
 *
 * <p><b>这里不做角色判定</b>：本类只回答"是否登录"，角色判定交给 {@link RoleInterceptor} 上的
 * {@link com.csagent.common.context.RequireRole}。原因是角色与具体方法绑定，
 * 写在路径规则里会随 Controller 拆分而失效；写在方法上则跟着代码走（AGENTS.md §9.4）。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 放行路径清单。<b>新增放行必须在此处写明理由</b>，否则下一任维护者无法判断
     * 它是历史遗留还是刻意为之，通常会"顺手删掉"，导致登录页 401。
     */
    private static final String[] PUBLIC_PATHS = {
            // 登录接口本身不可能要求登录
            "/api/auth/login",
            "/api/platform/auth/login",
            // 用户侧客服窗：匿名访客入口，嵌在客户网站上，不存在登录态。
            // 这是 AGENTS.md §8.1① 明确允许的唯一例外，其安全性由四件事共同保证：
            // ① 独立 Controller（WidgetController），不与租户后台接口共用；
            // ② 入口限流（WidgetRateLimiter），防止脚本刷爆 LLM 额度；
            // ③ 强制关闭工具调用，访客只能问答，不能执行业务动作；
            // ④ 所有按 id 的访问都校验会话属于该访客。
            "/api/widget/**",
            // 健康检查：AGENTS.md §7 的交付验收项与容器探针都依赖它，不能要求令牌
            "/actuator/health",
            "/actuator/info",
            // Spring Boot 的错误转发入口，拦截它会把 404 变成 401，掩盖真实问题
            "/error"
    };

    /**
     * 密码哈希：BCrypt。
     *
     * <p>不用 MD5/SHA256 的原因：它们太快，适合暴力破解；BCrypt 自带随机盐 +
     * 可调计算成本，拖库后破解成本高几个数量级。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   RestAuthenticationEntryPoint entryPoint) throws Exception {
        // 手动 new，不注册为 Bean：理由见 JwtAuthenticationFilter 类注释（避免被 Boot 重复注册）
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);

        http
                // 无 Cookie / 无 session，CSRF 攻击依赖浏览器自动携带凭据，这里不存在该前提。
                // 保留 CSRF 会要求每个 POST 带 token，对纯 API 是纯负担。
                .csrf(AbstractHttpConfigurer::disable)
                // 不维护服务端 session：任意实例都能独立校验 JWT，水平扩容无需粘性会话
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 关掉表单登录与 Basic 认证：它们的默认响应是 HTML 页面/浏览器弹窗，
                // 与 SPA 前端不兼容，也会让学生误以为存在账号密码表单
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // CORS 预检不带 Authorization 头，拦掉会导致前端所有跨域请求失败
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
