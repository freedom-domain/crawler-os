package com.collect.common.exception;

import com.collect.common.result.R;
import com.collect.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Object handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        if (isBinaryRequest(request)) {
            // 图片/二进制请求出错时返回 404 空响应，避免 R 无法序列化为 image/* 类型
            return ResponseEntity.notFound().build();
        }
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : fe.getField())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : fe.getField())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常", e);
        if (isBinaryRequest(request)) {
            return ResponseEntity.notFound().build();
        }
        return R.fail(ResultCode.FAIL.getCode(), "系统异常: " + e.getMessage());
    }

    /**
     * 判断请求是否为二进制资源（图片/文件流等）。
     * 这类请求的 Content-Type 非 JSON，GlobalExceptionHandler 无法返回 R（JSON），
     * 应返回空 404 让前端按图片加载失败处理。
     */
    private boolean isBinaryRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }
        return accept.contains("image/") || accept.contains("application/octet-stream")
                || accept.contains("multipart/");
    }
}
