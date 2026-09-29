package com.collect.common.config;

import com.collect.common.security.LoginUserInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 {@link LoginUserInterceptor}，使各业务服务能从请求头解析登录用户。
 * 仅在存在 Spring MVC 的环境（非 WebFlux 网关）下生效。
 */
@Configuration
@ConditionalOnClass(WebMvcConfigurer.class)
@RequiredArgsConstructor
public class SecurityWebConfig implements WebMvcConfigurer {

    private final LoginUserInterceptor loginUserInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(loginUserInterceptor)
                .addPathPatterns("/api/**");
    }
}
