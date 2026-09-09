package com.co.kc.imchat.plugin.web;

import com.co.kc.imchat.plugin.web.advice.ErrorAdvice;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ImWebAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ImWebAutoConfiguration.class));

    @Test
    void loadsCoreWebCapabilitiesWithoutPermissiveCorsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ErrorAdvice.class);
            assertThat(context).hasSingleBean(ResultAdvice.class);
            assertThat(context).hasBean("httpRequestContextFilterRegistration");
            FilterRegistrationBean<?> registration = context.getBean(
                    "httpRequestContextFilterRegistration",
                    FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isNegative();
            assertThat(context).doesNotHaveBean("imCorsFilterRegistration");
        });
    }

    @Test
    void registersCorsOnlyWhenExplicitlyConfigured() {
        contextRunner
                .withPropertyValues(
                        "im.web.cors.enabled=true",
                        "im.web.cors.allowed-origin-patterns[0]=https://admin.example.com")
                .run(context -> assertThat(context)
                        .hasBean("imCorsFilterRegistration"));
    }
}
