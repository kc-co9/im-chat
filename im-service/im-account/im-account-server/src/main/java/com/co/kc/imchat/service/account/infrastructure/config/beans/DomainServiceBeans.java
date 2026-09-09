package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.plugin.session.properties.JwtProperties;
import com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.service.account.domain.session.service.SessionTokenCodec;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import com.co.kc.imchat.service.account.domain.user.service.ManagedUserService;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.service.account.infrastructure.domain.service.BcryptPasswordService;
import com.co.kc.imchat.service.account.infrastructure.domain.service.JwtSessionTokenCodec;
import com.co.kc.imchat.service.account.infrastructure.security.JwtFingerprintKeyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 账号领域服务 Bean 装配。
 */
@Configuration
public class DomainServiceBeans {

    @Bean
    public PasswordService passwordService() {
        return new BcryptPasswordService();
    }

    @Bean
    public SessionTokenCodec sessionTokenCodec(
            JwtTokenCodec jwtTokenCodec,
            JwtProperties jwtProperties,
            JwtFingerprintKeyFactory fingerprintKeyFactory
    ) {
        return new JwtSessionTokenCodec(jwtTokenCodec, fingerprintKeyFactory.create(jwtProperties));
    }

    @Bean
    public SessionService sessionService(SessionTokenCodec sessionTokenCodec,
                                        SessionRepository sessionRepository) {
        return new SessionService(sessionTokenCodec, sessionRepository);
    }

    @Bean
    public JwtFingerprintKeyFactory jwtFingerprintKeyFactory() {
        return new JwtFingerprintKeyFactory();
    }

    @Bean
    public UserService userService(SnowflakeId snowflakeId,
                                   UserRepository userRepository,
                                   PasswordService passwordService) {
        return new UserService(userRepository, passwordService, snowflakeId);
    }

    @Bean
    public ManagedUserService managedUserService(ManagedUserRepository managedUserRepository) {
        return new ManagedUserService(managedUserRepository);
    }

}
