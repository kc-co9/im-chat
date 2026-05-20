package com.co.kc.imchat.interfaces.config;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.co.kc.imchat.interfaces.support.web.convert.IntegerCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.interfaces.support.web.convert.StringCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.interfaces.support.web.interceptor.HttpContextInterceptor;
import com.co.kc.imchat.common.serializer.BaseEnumSerializer;
import com.co.kc.imchat.common.model.enums.BaseEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;

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
                        "/login.html", "/register.html", "/chat.html", "/chat-vue.html",
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

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOriginPatterns(Collections.singletonList("*"));
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.addAllowedMethod("*");
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        // 设置过滤器的顺序
        bean.setOrder(0);
        return new CorsFilter(source);
    }

    /**
     * Web响应JSON配置
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperCustomization() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder
                .modules(JsonUtils.getJavaTimeModule())
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(BaseEnum.class, new BaseEnumSerializer());
    }
}
