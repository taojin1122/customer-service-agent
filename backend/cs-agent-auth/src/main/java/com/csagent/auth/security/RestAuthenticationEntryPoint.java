package com.csagent.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未登录时的统一响应：返回 HTTP 401 + 与业务接口同构的 JSON。
 *
 * <p><b>为什么必须自定义</b>：Spring Security 默认返回一个 HTML 登录页（或空 body 的 401）。
 * 前端是 SPA，拿到 HTML 会在解析 JSON 时报 "Unexpected token &lt;"，错误信息与真实原因
 * （没登录）完全无关，排查会跑偏。这里改成 JSON，前端拦截器才能统一跳登录页。</p>
 *
 * <p><b>为什么状态码必须是真 401 而不是 HTTP 200 + code 401</b>：网关、监控、前端 axios
 * 都靠 HTTP 状态码做分支。塞进 body 后，这些通用设施全部失效，只有本项目的前端能识别。</p>
 *
 * <p><b>为什么手拼 JSON 而不注入 ObjectMapper</b>：这里的响应体是固定常量，没有任何动态内容，
 * 为它引入序列化依赖不划算；而且本模块不保证一定在 Jackson 的 classpath 上
 * （Boot 4 的 {@code spring-boot-starter-web} 已不再传递 Jackson 2），
 * 注入会导致模块无法独立编译。</p>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * 字段顺序与 {@code com.csagent.common.result.Result} 保持一致，
     * 前端拦截器对「401 响应」和「业务响应」可以走同一套解析逻辑。
     */
    private static final String UNAUTHORIZED_BODY =
            "{\"success\":false,\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
