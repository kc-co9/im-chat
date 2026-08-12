package com.co.kc.imchat.service.social.infrastructure.config.beans;

import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.common.identity.snowflake.impl.StaticSnowflakeMachineId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 社交基础 Bean 装配。
 */
@Configuration
@ConditionalOnProperty(prefix = "im.social.provider", name = "enabled", havingValue = "true")
public class BasicBeans {

    @Bean
    public SnowflakeId snowflakeId() {
        return new SnowflakeId(new StaticSnowflakeMachineId(1, 1));
    }
}
