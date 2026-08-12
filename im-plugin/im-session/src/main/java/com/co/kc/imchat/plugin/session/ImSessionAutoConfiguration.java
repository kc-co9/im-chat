package com.co.kc.imchat.plugin.session;

import com.co.kc.imchat.plugin.session.token.JwtTokenService;
import com.co.kc.imchat.plugin.session.token.TokenService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ImSessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TokenService tokenService() {
        return new JwtTokenService();
    }
}
