package com.csagent.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应封装
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    /** 成功标志 */
    private boolean success;
    /** 业务码：0 成功，非 0 失败 */
    private int code;
    /** 提示消息 */
    private String message;
    /** 数据 */
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.success = true;
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.success = false;
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }
}
