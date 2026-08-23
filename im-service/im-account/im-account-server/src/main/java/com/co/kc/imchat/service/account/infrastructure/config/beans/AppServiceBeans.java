package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.co.kc.imchat.service.account.application.SessionAppService;
import com.co.kc.imchat.service.account.adapter.broker.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
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
    public SessionAppService sessionAppService(SessionRepository sessionRepository,
                                               UserService userService,
                                               SessionService sessionService,
                                               SessionConnectionAdapter sessionConnectionAdapter,
                                               DistributedLockTemplate distributedLockTemplate) {
        return new SessionAppService(
                sessionRepository,
                userService,
                sessionService,
                sessionConnectionAdapter,
                distributedLockTemplate);
    }

}
