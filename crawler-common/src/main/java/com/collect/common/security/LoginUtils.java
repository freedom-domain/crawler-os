package com.collect.common.security;

import com.collect.common.exception.BizException;
import com.collect.common.result.ResultCode;

public final class LoginUtils {

    private static ThreadLocal<LoginUser> userHolder = new ThreadLocal<>();

    private LoginUtils() {
    }

    public static void setLoginUser(LoginUser user) {
        userHolder.set(user);
    }

    public static LoginUser getLoginUser() {
        LoginUser user = userHolder.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    public static void remove() {
        userHolder.remove();
    }
}
