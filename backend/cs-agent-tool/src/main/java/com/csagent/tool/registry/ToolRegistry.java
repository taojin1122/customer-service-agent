package com.csagent.tool.registry;

import com.csagent.tool.annotation.AgentTool;
import com.csagent.tool.api.AgentToolProvider;
import com.csagent.tool.definition.ToolDefinition;
import com.csagent.tool.result.ToolResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心。
 * <p>
 * 通过注入所有 {@link AgentToolProvider} 实现（Spring 自动收集），
 * 扫描其标注 {@link AgentTool} 的方法，构建统一工具注册表：
 * - 工具名：方法名（如 query_repair_order）
 * - 参数 Schema：由方法参数自动推导（名称 + 类型 + 必填）
 * 同时提供「工具执行」能力：按名查找方法、反射调用、结果统一封装。
 * <p>
 * 采用接口注入而非全量 Bean 扫描，避免循环依赖且扫描范围明确。
 */
@Slf4j
@Component
public class ToolRegistry {

    /** 工具提供者列表（Spring 自动收集所有实现） */
    private final List<AgentToolProvider> toolProviders;

    /** 工具名 -> 工具元数据 */
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();
    /** 工具名 -> 目标 Bean */
    private final Map<String, Object> targetBeans = new ConcurrentHashMap<>();
    /** 工具名 -> 目标方法 */
    private final Map<String, Method> targetMethods = new ConcurrentHashMap<>();

    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public ToolRegistry(List<AgentToolProvider> toolProviders) {
        this.toolProviders = toolProviders;
    }

    @PostConstruct
    public void scanAndRegister() {
        for (AgentToolProvider provider : toolProviders) {
            Class<?> targetClass = AopUtils.getTargetClass(provider);
            for (Method method : ReflectionUtils.getAllDeclaredMethods(targetClass)) {
                AgentTool annotation = method.getAnnotation(AgentTool.class);
                if (annotation != null) {
                    register(provider, method, annotation);
                }
            }
        }
        log.info("Tool 注册完成，共 {} 个工具：{}", definitions.size(), String.join(", ", definitions.keySet()));
    }

    private void register(Object bean, Method method, AgentTool annotation) {
        String toolName = method.getName();
        if (definitions.containsKey(toolName)) {
            throw new IllegalStateException("工具名冲突：" + toolName + "，工具名（方法名）必须唯一");
        }
        ToolDefinition def = new ToolDefinition();
        // 工具名称（LLM 调用标识，须唯一，建议 verb_noun 格式）
        def.setName(toolName);
        // 工具描述（LLM 判断何时调用的依据，要写清楚用途与适用场景）
        def.setDescription(annotation.description());
        // 所属业务域
        def.setDomain(annotation.domain());
        def.setHighRisk(annotation.highRisk());
        def.setParametersSchema(buildParametersSchema(method));

        definitions.put(toolName, def);
        targetBeans.put(toolName, bean);
        targetMethods.put(toolName, method);
    }

    /**
     * 由方法参数自动推导 JSON Schema
     */
    private Map<String, Object> buildParametersSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            String name = (paramNames != null && paramNames[i] != null) ? paramNames[i] : "arg" + i;
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", mapType(p.getType()));
            prop.put("description", p.getType().getSimpleName() + " 类型参数");
            properties.put(name, prop);
            if (p.getType() == String.class) {
                required.add(name);
            }
        }
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private String mapType(Class<?> type) {
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        return "string";
    }

    /**
     * 执行工具调用
     *
     * @param toolName   工具名
     * @param argsJson   LLM 生成的参数 JSON
     * @return 执行结果
     */
    public ToolResult execute(String toolName, Map<String, Object> argsJson) {
        Method method = targetMethods.get(toolName);
        if (method == null) {
            return ToolResult.fail(toolName, "工具不存在：" + toolName);
        }
        try {
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            for (int i = 0; i < parameters.length; i++) {
                Parameter p = parameters[i];
                String name = (paramNames != null && paramNames[i] != null) ? paramNames[i] : "arg" + i;
                Object value = argsJson != null ? argsJson.get(name) : null;
                args[i] = convert(p.getType(), value);
            }
            Object result = ReflectionUtils.invokeMethod(method, targetBeans.get(toolName), args);
            if (result == null) {
                return ToolResult.ok(toolName, "执行成功");
            }
            return ToolResult.ok(toolName, result.toString());
        } catch (Exception e) {
            log.error("工具执行失败：{}", toolName, e);
            return ToolResult.fail(toolName, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
    }

    private Object convert(Class<?> type, Object value) {
        if (value == null) {
            return null;
        }
        if (type == String.class) {
            return String.valueOf(value);
        }
        if (type == int.class || type == Integer.class) {
            return ((Number) value).intValue();
        }
        if (type == long.class || type == Long.class) {
            return ((Number) value).longValue();
        }
        if (type == double.class || type == Double.class) {
            return ((Number) value).doubleValue();
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.valueOf(String.valueOf(value));
        }
        return value;
    }

    public List<ToolDefinition> listDefinitions() {
        return new ArrayList<>(definitions.values());
    }

    public ToolDefinition getDefinition(String toolName) {
        return definitions.get(toolName);
    }

    public boolean contains(String toolName) {
        return definitions.containsKey(toolName);
    }
}
