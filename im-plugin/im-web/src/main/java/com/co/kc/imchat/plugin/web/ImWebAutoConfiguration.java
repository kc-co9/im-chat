package com.co.kc.imchat.plugin.web;

import com.co.kc.imchat.common.model.enums.BaseEnum;
import com.co.kc.imchat.common.serializer.BaseEnumSerializer;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.web.advice.ErrorAdvice;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import com.co.kc.imchat.plugin.web.convert.IntegerCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.plugin.web.convert.StringCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.plugin.web.logging.LogProperties;
import com.co.kc.imchat.plugin.web.logging.filter.LoggingFilter;
import com.co.kc.imchat.plugin.web.logging.filter.MdcFilter;
import com.co.kc.imchat.plugin.web.mvc.ImWebMvcInterceptor;
import com.co.kc.imchat.plugin.web.session.UserContextInterceptor;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;
import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LogProperties.class)
public class ImWebAutoConfiguration implements WebMvcConfigurer {
    private final List<ImWebMvcInterceptor> interceptors;

    public ImWebAutoConfiguration(ObjectProvider<ImWebMvcInterceptor> interceptors) {
        this.interceptors = interceptors.stream().toList();
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new IntegerCodeToBaseEnumConverterFactory());
        registry.addConverterFactory(new StringCodeToBaseEnumConverterFactory());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        for (ImWebMvcInterceptor interceptor : interceptors) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns(interceptor.includePathPatterns())
                    .excludePathPatterns(interceptor.excludePathPatterns());
        }
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer imJacksonObjectMapperCustomization() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder
                .modules(JsonUtils.getJavaTimeModule())
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(BaseEnum.class, new BaseEnumSerializer());
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorAdvice imErrorAdvice() {
        return new ErrorAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResultAdvice imResultAdvice() {
        return new ResultAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    public MdcFilter imMdcFilter() {
        return new MdcFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public LoggingFilter imLoggingFilter(LogProperties logProperties) {
        return new LoggingFilter(logProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserContextInterceptor imUserContextInterceptor() {
        return new UserContextInterceptor();
    }

    @Bean
    public WebMvcConfigurer imUserContextWebMvcConfigurer(UserContextInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/**");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<CorsFilter> imCorsFilterRegistration() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOriginPatterns(Collections.singletonList("*"));
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.addAllowedMethod("*");
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(0);
        return bean;
    }
}
