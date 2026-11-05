package com.csagent.auth.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册角色校验拦截器。
 *
 * <p><b>为什么单独一个配置类而不是塞进 server 模块的 WebConfig</b>：拦截器是 auth 模块的组件，
 * 注册代码留在同一模块里，模块被移除时不会在 server 留下悬空配置。</p>
 *
 * <p><b>为什么只拦 {@code /api/**}</b>：actuator 与静态资源没有 {@code @RequireRole}，
 * 拦下来只是白白多一次反射查找。</p>
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    public AuthWebConfig(RoleInterceptor roleInterceptor) {
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor).addPathPatterns("/api/**");
    }
}
