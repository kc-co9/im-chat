package com.co.kc.imchat.management.iam.infrastructure.config.beans;

import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamSnowflakeOwnershipTest {

    @Test
    void iamDoesNotOwnSnowflakeConfigurationOrFactory() {
        assertThatThrownBy(() -> Class.forName(
                "com.co.kc.imchat.management.iam.infrastructure.config.properties.IamSnowflakeProperties"))
                .isInstanceOf(ClassNotFoundException.class);
        boolean declaresSnowflakeId = Arrays.stream(IamServiceBeans.class.getDeclaredMethods())
                .anyMatch(method -> SnowflakeId.class.equals(method.getReturnType()));
        assertThat(declaresSnowflakeId).isFalse();
    }

    @Test
    void configuresRedisSnowflakeIdentity() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("im.identity.snowflake.mode"))
                .isEqualTo("REDIS");
        assertThat(environment.getProperty("im.identity.snowflake.data-center-id"))
                .isEqualTo("0");
    }
}
