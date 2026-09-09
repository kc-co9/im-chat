package com.co.kc.imchat.plugin.session;

import com.co.kc.imchat.plugin.session.properties.JwtProperties;
import com.co.kc.imchat.plugin.session.properties.SessionWebProperties;
import com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec;
import com.co.kc.imchat.plugin.session.token.codec.JwtSigningKeyFactory;
import com.co.kc.imchat.plugin.session.web.UserContextInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * IM Session 基础能力自动配置。
 *
 * <p>统一提供 JWT 编解码能力，并在 Servlet 应用中注册用户上下文拦截器。JWT 能力仍由
 * {@code im.session.jwt.enabled} 显式控制，非 Web 应用不会创建 Servlet 适配组件。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class ImSessionAutoConfiguration {

    /**
     * 创建 JWT 签名密钥工厂。
     *
     * @return JWT 签名密钥工厂
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.session.jwt", name = "enabled", havingValue = "true")
    public JwtSigningKeyFactory jwtSigningKeyFactory() {
        return new JwtSigningKeyFactory();
    }

    /**
     * 根据 Session JWT 配置创建 Token 编解码器。
     *
     * @param properties JWT 配置
     * @param signingKeyFactory 签名密钥工厂
     * @return JWT Token 编解码器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.session.jwt", name = "enabled", havingValue = "true")
    public JwtTokenCodec jwtTokenCodec(
            JwtProperties properties,
            JwtSigningKeyFactory signingKeyFactory) {
        return new JwtTokenCodec(
                signingKeyFactory.create(properties),
                properties.getIssuer(),
                properties.getAccessTokenTtl(),
                properties.getRefreshTokenTtl(),
                Clock.systemUTC());
    }

    /**
     * Session 与 Servlet 请求的内部适配配置。
     *
     * <p>该配置仅在 Servlet 应用中生效，避免通用 Session 能力强制创建 Web 组件。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @EnableConfigurationProperties(SessionWebProperties.class)
    static class ServletSessionConfiguration {

        /**
         * 注册用户上下文拦截器。
         *
         * @param properties Session Web 配置
         * @return 用户上下文拦截器
         */
        @Bean
        @ConditionalOnMissingBean
        UserContextInterceptor userContextInterceptor(SessionWebProperties properties) {
            return new UserContextInterceptor(properties);
        }

        /**
         * 将用户上下文拦截器应用到全部请求路径，公开路径由拦截器配置排除。
         *
         * @param interceptor 用户上下文拦截器
         * @return MVC 配置扩展
         */
        @Bean
        WebMvcConfigurer sessionWebMvcConfigurer(UserContextInterceptor interceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor).addPathPatterns("/**");
                }
            };
        }
    }
}
