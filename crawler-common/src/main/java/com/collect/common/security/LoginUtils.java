package com.collect.common.security;

import com.collect.common.exception.BizException;
import com.collect.common.result.ResultCode;

import java.util.Set;

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

    /**
     * 判断当前登录用户是否拥有指定权限码。
     * 管理员（roleCode = admin）拥有全部权限。
     */
    public static boolean hasPermission(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        LoginUser user = getLoginUser();
        if ("admin".equals(user.getRoleCode())) {
            return true;
        }
        Set<String> perms = user.getPermissions();
        return perms != null && perms.contains(code);
    }

    public static void remove() {
        userHolder.remove();
    }
}
