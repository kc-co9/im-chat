package com.co.kc.imchat.infrastructure.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.co.kc.imchat.support.web.convert.IntegerCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.support.web.convert.StringCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.support.web.interceptor.HttpContextInterceptor;
import com.co.kc.imchat.support.web.serializer.BaseEnumSerializer;
import com.co.kc.imchat.model.enums.BaseEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
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
                .excludePathPatterns(
                        "/user/signUp", "/user/signIn",
                        "/login.html", "/register.html", "/chat.html",
                        "/css/**", "/js/**", "/img/**", "/fonts/**");
    }

    /**
     * 枚举类的转换器工厂 addConverterFactory
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new IntegerCodeToBaseEnumConverterFactory());
        registry.addConverterFactory(new StringCodeToBaseEnumConverterFactory());
    }

    /**
     * 跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Web响应JSON配置
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperCustomization() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(BaseEnum.class, new BaseEnumSerializer());
    }
}
