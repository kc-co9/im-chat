package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.service.account.infrastructure.domain.service.BcryptPasswordService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 账号领域服务 Bean 装配。
 */
@Configuration
@ConditionalOnProperty(prefix = "im.account.provider", name = "enabled", havingValue = "true")
public class DomainServiceBeans {

    @Bean
    public PasswordService passwordService() {
        return new BcryptPasswordService();
    }

    @Bean
    public UserService userService(SnowflakeId snowflakeId,
                                   UserRepository userRepository,
                                   PasswordService passwordService) {
        return new UserService(userRepository, passwordService, snowflakeId);
    }
}
