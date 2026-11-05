package com.csagent.auth.security;

import com.csagent.common.context.CurrentUser;
import com.csagent.common.context.RequireRole;
import com.csagent.common.context.TenantContext;
import com.csagent.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色校验拦截器：执行 {@link RequireRole} 声明的白名单判定。
 *
 * <p><b>为什么不用 Spring Security 的方法级注解</b>：见 {@link RequireRole} 的说明——
 * SpEL 表达式写错不会在启动时暴露，只会在调用时静默拒绝，排查成本高。</p>
 *
 * <p><b>方法上的注解优先于类上</b>：允许整个 Controller 定一个默认角色，
 * 个别方法再放宽或收紧，避免在每个方法上重复标注。</p>
 *
 * <p><b>为什么抛异常而不是直接写 403 响应</b>：交给 {@code GlobalExceptionHandler} 统一出口，
 * 保证错误响应格式与其它接口一致；若这里手写响应，格式会逐渐与全局不一致。</p>
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            // 静态资源、actuator 等非 Controller 请求不参与角色判定
            return true;
        }

        RequireRole requireRole = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RequireRole.class);
        if (requireRole == null) {
            requireRole = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequireRole.class);
        }
        if (requireRole == null) {
            // 未标注 = 只要求已登录，不额外限制角色。默认拒绝角色会让新增接口时忘标注
            // 直接导致坐席全部 403，业务先坏（AGENTS.md §9.4）
            return true;
        }

        // 走到这里说明 Security 已放行，但防御性再取一次：拦截器也可能被单独注册到别的链上
        CurrentUser user = TenantContext.require();
        for (String allowed : requireRole.value()) {
            if (allowed.equals(user.role())) {
                return true;
            }
        }

        throw new BusinessException(403, "无权限访问该功能");
    }
}
