package com.co.kc.imchat.plugin.web;

import com.co.kc.imchat.common.model.enums.BaseEnum;
import com.co.kc.imchat.common.serializer.BaseEnumSerializer;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.web.advice.ErrorAdvice;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import com.co.kc.imchat.plugin.web.context.HttpRequestContextFilter;
import com.co.kc.imchat.plugin.web.convert.IntegerCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.plugin.web.convert.StringCodeToBaseEnumConverterFactory;
import com.co.kc.imchat.plugin.web.logging.LogProperties;
import com.co.kc.imchat.plugin.web.logging.filter.LoggingFilter;
import com.co.kc.imchat.plugin.web.logging.filter.MdcFilter;
import com.co.kc.imchat.plugin.web.mvc.ImWebMvcInterceptor;
import com.co.kc.imchat.plugin.web.properties.WebCorsProperties;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * IM Servlet Web 基础能力自动配置。
 *
 * <p>统一注册 HTTP 结果处理、JSON 序列化、请求日志、请求上下文、MVC 扩展和按需启用的
 * CORS 能力。业务模块只需引入 {@code im-web}，无需逐项导入内部配置。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LogProperties.class)
public class ImWebAutoConfiguration implements WebMvcConfigurer {
    private final List<ImWebMvcInterceptor> interceptors;

    /**
     * 创建 Web 自动配置，并收集业务侧提供的 MVC 拦截器扩展。
     *
     * @param interceptors MVC 拦截器扩展
     */
    public ImWebAutoConfiguration(ObjectProvider<ImWebMvcInterceptor> interceptors) {
        this.interceptors = interceptors.stream().toList();
    }

    /**
     * 注册统一异常响应处理器。
     *
     * @return 异常响应处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorAdvice imErrorAdvice() {
        return new ErrorAdvice();
    }

    /**
     * 注册统一成功响应处理器。
     *
     * @return 成功响应处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public ResultAdvice imResultAdvice() {
        return new ResultAdvice();
    }

    /**
     * 注册项目统一的 Jackson 序列化规则。
     *
     * @return Jackson 构建器定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer imJacksonObjectMapperCustomization() {
        return builder -> builder
                .modules(JsonUtils.getJavaTimeModule())
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(BaseEnum.class, new BaseEnumSerializer());
    }

    /**
     * 注册 MDC 请求上下文过滤器。
     *
     * @return MDC 过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public MdcFilter imMdcFilter() {
        return new MdcFilter();
    }

    /**
     * 注册 HTTP 请求日志过滤器。
     *
     * @param properties 日志配置
     * @return 请求日志过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public LoggingFilter imLoggingFilter(LogProperties properties) {
        return new LoggingFilter(properties);
    }

    /**
     * 在 Spring Security 之前建立 HTTP 请求元数据上下文。
     *
     * @return 请求上下文过滤器注册信息
     */
    @Bean
    @ConditionalOnMissingBean(name = "httpRequestContextFilterRegistration")
    public FilterRegistrationBean<HttpRequestContextFilter> httpRequestContextFilterRegistration() {
        FilterRegistrationBean<HttpRequestContextFilter> registration =
                new FilterRegistrationBean<>(new HttpRequestContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /**
     * 注册项目通用枚举参数转换器。
     *
     * @param registry MVC 格式化器注册表
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new IntegerCodeToBaseEnumConverterFactory());
        registry.addConverterFactory(new StringCodeToBaseEnumConverterFactory());
    }

    /**
     * 注册业务模块提供的 MVC 拦截器及其包含、排除路径。
     *
     * @param registry MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        for (ImWebMvcInterceptor interceptor : interceptors) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns(interceptor.includePathPatterns())
                    .excludePathPatterns(interceptor.excludePathPatterns());
        }
    }

    /**
     * 显式启用 CORS 后才生效的内部配置，避免关闭跨域能力时创建无用配置 Bean。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "im.web.cors", name = "enabled", havingValue = "true")
    @EnableConfigurationProperties(WebCorsProperties.class)
    static class CorsWebConfiguration {

        /**
         * 注册跨域过滤器。
         *
         * @param properties CORS 配置
         * @return CORS 过滤器注册信息
         */
        @Bean
        @ConditionalOnMissingBean
        FilterRegistrationBean<CorsFilter> imCorsFilterRegistration(
                WebCorsProperties properties
        ) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
            configuration.setAllowedHeaders(properties.getAllowedHeaders());
            configuration.setAllowedMethods(properties.getAllowedMethods());
            configuration.setAllowCredentials(properties.getAllowCredentials());
            configuration.setMaxAge(properties.getMaxAge());

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);

            FilterRegistrationBean<CorsFilter> registration =
                    new FilterRegistrationBean<>(new CorsFilter(source));
            registration.setOrder(0);
            return registration;
        }
    }

}
