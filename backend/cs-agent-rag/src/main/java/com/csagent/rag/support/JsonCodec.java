package com.csagent.rag.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 知识库模块内部的 JSON 编解码。
 *
 * <p><b>为什么不用容器里的 {@code ObjectMapper} bean</b>：Spring Boot 4 把 Jackson 的自动配置
 * 拆到了独立模块，当前依赖树里没有那个 starter，容器中不存在 {@code ObjectMapper} bean
 * （启动时表现为 "No qualifying bean of type ObjectMapper"）。本模块只需要处理
 * {@code float[]} 与 {@code List<String>} 这种结构，自建一个静态实例即可，
 * 既不必为一个工具类去引额外 starter，也不会因为容器里 Jackson 配置变化而影响存储格式。</p>
 *
 * <p>代价是它不共享 Spring 的序列化配置（日期格式、命名策略等）。这里存的全是无格式敏感的类型，
 * 所以不构成问题；将来若要存带日期的结构，应改用容器里的 ObjectMapper。</p>
 */
public final class JsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonCodec() {
    }

    public static String write(Object value) throws Exception {
        return MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) throws Exception {
        return MAPPER.readValue(json, type);
    }

    public static List<String> readStringList(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<List<String>>() {
        });
    }
}
