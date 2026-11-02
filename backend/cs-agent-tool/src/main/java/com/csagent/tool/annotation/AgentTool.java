package com.csagent.tool.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法注解：标记一个方法为 Agent 可调用工具。
 * <p>
 * 用法：在 Spring Bean 的方法上标注，方法名即工具名，
 * 参数自动推导为 JSON Schema（支持 String / Integer / Long / Double / Boolean）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {

    /** 工具描述（LLM 判断调用时机，务必写清楚用途） */
    String description();

    /** 所属业务域 */
    String domain() default "general";

    /** 是否高权限操作（需用户确认） */
    boolean highRisk() default false;
}
