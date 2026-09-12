package com.collect.common.result;

import lombok.Data;

import java.io.Serializable;

@Data
public class R<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    private R() {
    }

    public static <T> R<T> ok() {
        return restResult(null, ResultCode.SUCCESS);
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, ResultCode.SUCCESS);
    }

    public static <T> R<T> ok(T data, String msg) {
        return restResult(data, new ResultCode(ResultCode.SUCCESS.getCode(), msg));
    }

    public static <T> R<T> fail() {
        return restResult(null, ResultCode.FAIL);
    }

    public static <T> R<T> fail(String msg) {
        return restResult(null, new ResultCode(ResultCode.FAIL.getCode(), msg));
    }

    public static <T> R<T> fail(int code, String msg) {
        return restResult(null, new ResultCode(code, msg));
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return restResult(null, resultCode);
    }

    public static <T> R<T> restResult(T data, ResultCode resultCode) {
        R<T> r = new R<>();
        r.setCode(resultCode.getCode());
        r.setMsg(resultCode.getMsg());
        r.setData(data);
        return r;
    }
}
