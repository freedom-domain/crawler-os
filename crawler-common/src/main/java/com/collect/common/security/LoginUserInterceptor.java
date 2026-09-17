package com.collect.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从请求头解析 JWT 并填充 {@link LoginUtils} 中的登录用户。
 * 网关已校验 token 合法性，这里仅做解析，解析失败时不阻断请求，
 * 由后续 {@link LoginUtils#getLoginUser()} 抛出 UNAUTHORIZED。
 */
@Slf4j
@Component
@ConditionalOnClass(HandlerInterceptor.class)
@RequiredArgsConstructor
public class LoginUserInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                LoginUser user = jwtUtils.parseToken(token);
                LoginUtils.setLoginUser(user);
            } catch (Exception e) {
                log.warn("解析登录用户失败: {}", e.getMessage());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex) {
        LoginUtils.remove();
    }
}
