package com.kim.omgchat.infrastructure.config;

import com.kim.omgchat.infrastructure.interceptor.HttpContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final HttpContextInterceptor httpContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/signUp", "/user/signIn");
    }
}
