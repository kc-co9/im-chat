package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.co.kc.imchat.plugin.session.token.TokenService;
import com.co.kc.imchat.service.account.application.AccountAppService;
import com.co.kc.imchat.service.account.application.AccountSessionAppService;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 账号应用服务 Bean 装配。
 */
@Configuration
@ConditionalOnProperty(prefix = "im.account.provider", name = "enabled", havingValue = "true")
public class AppServiceBeans {

    @Bean
    public UserAppService userAppService(UserRepository userRepository,
                                         UserService userService) {
        return new UserAppService(userRepository, userService);
    }

    @Bean
    public AccountAppService accountAppService(SessionRepository sessionRepository,
                                               UserService userService,
                                               TokenService tokenService) {
        return new AccountAppService(sessionRepository, userService, tokenService);
    }

    @Bean
    public AccountSessionAppService accountSessionAppService(SessionRepository sessionRepository) {
        return new AccountSessionAppService(sessionRepository);
    }

}
