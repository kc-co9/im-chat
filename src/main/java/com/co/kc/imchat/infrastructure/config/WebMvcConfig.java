package com.co.kc.imchat.infrastructure.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.co.kc.imchat.infrastructure.convert.IntegerCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.infrastructure.convert.StringCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.infrastructure.interceptor.HttpContextInterceptor;
import com.co.kc.imchat.infrastructure.serializer.BaseEnumSerializer;
import com.co.kc.imchat.model.enums.BaseEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
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

    /**
     * 枚举类的转换器工厂 addConverterFactory
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new IntegerCodeToBaseEnumConverterFactory());
        registry.addConverterFactory(new StringCodeToBaseEnumConverterFactory());
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
