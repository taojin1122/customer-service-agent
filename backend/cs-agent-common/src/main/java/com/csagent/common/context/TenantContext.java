package com.csagent.common.context;

import com.csagent.common.exception.BusinessException;

/**
 * 请求级身份上下文：一次请求内任何位置都能取到"我是谁、属于哪个租户"。
 *
 * <p><b>为什么需要它</b>：会话查询、知识库检索、工具执行都要按租户过滤。
 * 如果每层方法都多加一个 {@code tenantId} 参数，签名会层层污染且容易漏传一处；
 * 漏传的那一处就是跨租户数据泄露。收敛到一个上下文后，业务层写
 * {@link #requireTenantId()} 即可，不必关心值从哪来。</p>
 *
 * <p><b>为什么用 ThreadLocal 而不是 Reactor Context</b>：本项目是
 * {@code spring-boot-starter-web}（Servlet / MVC），不是 WebFlux，Reactor Context 在这里没有挂载点。
 * ThreadLocal 与 Servlet 的"一请求一线程"模型天然对齐。</p>
 *
 * <p><b>使用边界（重要）</b>：ThreadLocal 不会自动跨越线程切换。SSE 流式响应中，
 * Spring MVC 会把 {@code Flux} 的订阅交给异步任务执行，此时 ThreadLocal 已失效。
 * 因此在进入流式分支前必须显式捕获 {@link CurrentUser} 并在新线程中重新
 * {@link #set}，结束后 {@link #clear()}。漏掉这一步的表现是"偶尔"取不到租户，
 * 且只在并发压测时暴露——比稳定报错更难排查。</p>
 *
 * <p><b>清理责任</b>：由鉴权过滤器在 {@code finally} 中调用 {@link #clear()}。
 * 线程池复用线程，不清理会把上一个请求的租户带给下一个请求，这是最典型的越权成因之一。</p>
 */
public final class TenantContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 绑定当前线程的身份。只应由鉴权过滤器调用。
     */
    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /**
     * 取当前身份，未登录时返回 {@code null}。
     *
     * <p>仅用于"允许匿名访问"的入口（如用户侧客服窗）做可空判断；
     * 需要身份的业务代码应使用 {@link #require()}，避免拿到 null 后继续执行。
     */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /**
     * 取当前身份，未登录直接抛异常。
     *
     * <p>用 401 而不是 500：调用方需要据此区分"该重新登录"和"服务端出错"。</p>
     */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return user;
    }

    /**
     * 取当前租户 ID，未登录直接抛异常。
     *
     * <p>平台账号没有租户，调用本方法说明该接口被设计成"只能租户访问"，
     * 因此平台账号调用同样视为非法，抛 403 而不是返回 null——返回 null 会一路传到 SQL 变成
     * {@code WHERE tenant_id IS NULL}，也就是"查到全部租户的数据"。</p>
     */
    public static String requireTenantId() {
        CurrentUser user = require();
        if (user.tenantId() == null) {
            throw new BusinessException(403, "当前账号不属于任何租户，无法访问该资源");
        }
        return user.tenantId();
    }

    /**
     * 清除当前线程的身份。必须在过滤器 {@code finally} 里调用，理由见类注释。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
