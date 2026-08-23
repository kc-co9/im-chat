package com.co.kc.imchat.plugin.session;

import com.co.kc.imchat.plugin.session.properties.JwtProperties;
import com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec;
import com.co.kc.imchat.plugin.session.token.codec.JwtSigningKeyFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class ImSessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.session.jwt", name = "enabled", havingValue = "true")
    public JwtSigningKeyFactory jwtSigningKeyFactory() {
        return new JwtSigningKeyFactory();
    }

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
}
