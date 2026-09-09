package com.co.kc.imchat.management.iam.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class IamOpenApiConfigTest {

    @Test
    void exposesSpringdocAtTheIamDocumentationPath() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load(
                        "application.yml",
                        new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        assertThatCode(() -> Class.forName("org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc"))
                .doesNotThrowAnyException();
        assertThat(environment.getProperty("springdoc.swagger-ui.path"))
                .isEqualTo("/api/doc.html");
        assertThat(environment.getProperty("springdoc.api-docs.path"))
                .isEqualTo("/v3/api-docs");
    }
}
