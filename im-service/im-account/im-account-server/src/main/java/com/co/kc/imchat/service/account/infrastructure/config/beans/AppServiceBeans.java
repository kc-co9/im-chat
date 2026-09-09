package com.co.kc.imchat.service.account.infrastructure.config.beans;

import com.co.kc.imchat.service.account.application.SessionAppService;
import com.co.kc.imchat.service.account.adapter.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.application.ManagedUserAppService;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import com.co.kc.imchat.service.account.domain.user.service.ManagedUserService;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 账号应用服务 Bean 装配。
 */
@Configuration
public class AppServiceBeans {

    @Bean
    public UserAppService userAppService(UserRepository userRepository,
                                         UserService userService,
                                         PasswordService passwordService) {
        return new UserAppService(userRepository, userService, passwordService);
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

    @Bean
    public ManagedUserAppService managedUserAppService(
            ManagedUserRepository managedUserRepository,
            PasswordService passwordService,
            ManagedUserService managedUserService,
            SessionService sessionService,
            SessionConnectionAdapter sessionConnectionAdapter,
            AfterTransactionCommitTemplate afterTransactionCommitTemplate,
            DistributedLockTemplate distributedLockTemplate) {
        return new ManagedUserAppService(
                managedUserRepository,
                passwordService,
                managedUserService,
                sessionService,
                sessionConnectionAdapter,
                afterTransactionCommitTemplate,
                distributedLockTemplate);
    }

}
