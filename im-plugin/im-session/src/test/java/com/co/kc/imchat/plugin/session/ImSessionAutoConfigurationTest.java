package com.co.kc.imchat.plugin.session;

import com.co.kc.imchat.plugin.session.token.JwtTokenService;
import com.co.kc.imchat.plugin.session.token.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ImSessionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ImSessionAutoConfiguration.class);

    @Test
    void providesDefaultTokenService() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TokenService.class);
            assertThat(context.getBean(TokenService.class)).isInstanceOf(JwtTokenService.class);
        });
    }
}
