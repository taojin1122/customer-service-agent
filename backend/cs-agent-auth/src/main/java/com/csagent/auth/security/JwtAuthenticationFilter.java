package com.csagent.auth.security;

import com.csagent.auth.jwt.JwtService;
import com.csagent.common.context.CurrentUser;
import com.csagent.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * JWT 鉴权过滤器：解析令牌 → 写入 {@link TenantContext} 与 Spring Security 上下文。
 *
 * <p><b>为什么不做成 {@code @Component}</b>：Spring Boot 会把容器里所有 {@code Filter} 类型的 Bean
 * 自动注册到 Servlet 链上。若它既是 Bean 又被加进 Security 链，同一个请求会被解析两次——
 * 平时只是浪费，但 {@code finally} 里的 {@code clear()} 会在第二次执行结束时把身份清掉，
 * 表现为"登录了却偶发 401"。所以它只由 {@link SecurityConfig} 手动 new 出来，
 * 不进入容器。</p>
 *
 * <p><b>为什么解析失败只放行不拦截</b>：本过滤器只负责"有令牌就识别身份"，不负责"没身份就拒绝"。
 * 是否放行由 {@code SecurityConfig} 的 {@code anyRequest().authenticated()} 统一裁决。
 * 若在这里直接 401，登录接口本身也会被拦（它不需要令牌）。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Optional<CurrentUser> user = extractToken(request).flatMap(jwtService::parse);

            user.ifPresent(u -> {
                // 业务侧从这里取租户，不读任何前端传的 header / body（AGENTS.md §8.1①）
                TenantContext.set(u);

                // 同时写入 Security 上下文，让 authenticated() 判定通过。
                // 权限列表只用角色本身（不带 ROLE_ 前缀），因为授权判定走自研 @RequireRole，
                // 不经过 hasRole() 表达式，加前缀只会在排查时多一层转换。
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        u, null, List.of(new SimpleGrantedAuthority(u.role())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });

            filterChain.doFilter(request, response);
        } finally {
            // 必须清理：Servlet 容器复用线程，残留的身份会让下一个请求"继承"上一个用户的租户，
            // 这是最典型也最难复现的越权成因
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
