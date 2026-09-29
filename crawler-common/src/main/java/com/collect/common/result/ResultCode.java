package com.collect.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResultCode {

    private int code;
    private String msg;

    public static final ResultCode SUCCESS = new ResultCode(200, "操作成功");
    public static final ResultCode FAIL = new ResultCode(500, "操作失败");
    public static final ResultCode PARAM_ERROR = new ResultCode(400, "参数错误");
    public static final ResultCode UNAUTHORIZED = new ResultCode(401, "未登录或登录已过期");
    public static final ResultCode FORBIDDEN = new ResultCode(403, "无权限访问");
    public static final ResultCode NOT_FOUND = new ResultCode(404, "资源不存在");
    public static final ResultCode TOO_MANY_REQUESTS = new ResultCode(429, "请求过于频繁");
}
