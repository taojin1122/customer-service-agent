package com.csagent.common.context;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级角色校验：标注后只有指定角色的账号才能调用。
 *
 * <p><b>为什么不用 Spring Security 的 {@code @PreAuthorize}</b>：本项目的角色只有两三个，
 * 且判定只需要"当前角色是否在白名单里"这一种逻辑。{@code @PreAuthorize} 需要 SpEL
 * （{@code hasAnyRole('ADMIN')}），字符串表达式写错了在启动时不会报错、只在调用时静默拒绝，
 * 排查成本高于收益。用普通注解 + 拦截器，判定逻辑是普通 Java 代码，可断点可单测。</p>
 *
 * <p><b>为什么默认拒绝</b>：未标注 {@code @RequireRole} 的接口只要求"已登录"，
 * 不额外限制角色——这是刻意的。若默认要求管理员，新增接口时忘了标注会导致
 * 坐席全部 403，业务先坏；而"默认登录即可"配合菜单白名单，风险可控且不阻断开发。
 * 真正需要严格限制的（知识库写、坐席管理、用量）必须显式标注。</p>
 *
 * <p><b>标注位置</b>：可以标在类上（整个 Controller 统一限制）或方法上（覆盖类上的设置）。</p>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 允许访问的角色字面量，见 {@link com.csagent.common.constant.RoleConstants}。
     * 多个角色之间是"或"的关系。
     */
    String[] value();
}
