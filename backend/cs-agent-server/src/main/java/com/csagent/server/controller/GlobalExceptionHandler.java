package com.csagent.server.controller;

import com.csagent.common.exception.BusinessException;
import com.csagent.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一翻译成 {@link Result} 结构。
 *
 * <p><b>为什么 401 / 403 要返回真实的 HTTP 状态码，其余保持 200</b>：</p>
 * <p>401（未登录）和 403（无权限）是唯一两类<b>需要通用设施识别</b>的错误——
 * 前端 axios 拦截器要靠 401 统一跳登录页，网关和监控要靠它区分"正常业务拒绝"与"鉴权失败"。
 * 把它们塞进 body 的 {@code code} 字段，就只有本项目前端能识别，任何标准组件都失效。</p>
 *
 * <p>其余错误（400 参数、500 业务失败）暂时保留 HTTP 200，是刻意的<b>收敛改动面</b>：
 * 现有前端与已有接口全部依赖"HTTP 200 + body.success"的约定，一次性全改成标准状态码
 * 会把本次改造的回归范围放大到所有接口。后续按 §8.3 逐项迁移时再统一。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = switch (e.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.OK;
        };
        // 鉴权类错误用 warn：它们是安全事件，需要在日志里能直接筛出来
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            log.warn("[鉴权] 拒绝访问：code={} message={}", e.getCode(), e.getMessage());
        } else {
            log.warn("业务异常：{}", e.getMessage());
        }
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 参数校验失败（{@code @Valid} 触发）。
     *
     * <p>不处理的话会落到下面的 {@code Exception} 分支，返回"系统繁忙"+ 500，
     * 用户看到的是系统故障，实际只是少填了一个字段。</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("请求参数不合法");
        log.warn("参数校验失败：{}", message);
        return Result.fail(400, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常：{}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 这里保留原始异常堆栈：非预期异常是排查线索，不能只记一句"系统繁忙"
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试：" + e.getMessage());
    }
}
