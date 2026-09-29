package com.collect.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 从请求头解析 JWT 并填充 {@link LoginUtils} 中的登录用户。
 * 每次访问刷新 token 过期时间（滑动过期）。
 */
@Slf4j
@Component
@ConditionalOnClass(HandlerInterceptor.class)
@RequiredArgsConstructor
public class LoginUserInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${jwt.expire:7200}")
    private long expireSeconds;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                LoginUser user = jwtUtils.parseToken(token);
                LoginUtils.setLoginUser(user);
                // 每次访问刷新 token 过期时间
                refreshTokenExpire(token);
            } catch (Exception e) {
                log.warn("解析登录用户失败: {}", e.getMessage());
            }
        }
        return true;
    }

    private void refreshTokenExpire(String token) {
        if (redisTemplate == null) return;
        try {
            String jti = jwtUtils.getTokenId(token);
            if (jti != null) {
                redisTemplate.expire("token:" + jti, expireSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.debug("刷新 token 过期时间失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex) {
        LoginUtils.remove();
    }
}
