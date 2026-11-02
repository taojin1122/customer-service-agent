package com.csagent.common.util;

import java.util.UUID;

/**
 * ID 生成工具
 */
public final class IdUtil {

    private IdUtil() {
    }

    /**
     * 生成无横线 UUID
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
