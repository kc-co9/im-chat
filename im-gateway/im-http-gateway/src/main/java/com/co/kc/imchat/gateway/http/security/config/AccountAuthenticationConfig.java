package com.co.kc.imchat.gateway.http.security.config;

import com.co.kc.imchat.gateway.http.security.authentication.AuthenticationManager;
import com.co.kc.imchat.service.account.facade.AccountService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Configuration
public class AccountAuthenticationConfig {
    private static final Duration AUTHENTICATION_TIMEOUT = Duration.ofSeconds(1);

    @DubboReference(interfaceClass = AccountService.class, version = "1.0.0", timeout = 800, retries = 0)
    private AccountService accountService;

    @Bean(destroyMethod = "dispose")
    public Scheduler authScheduler() {
        return Schedulers.newBoundedElastic(8, 1_000, "account-authentication");
    }

    @Bean
    public ReactiveAuthenticationManager authenticationManager(Scheduler authScheduler) {
        return new AuthenticationManager(accountService, authScheduler, AUTHENTICATION_TIMEOUT);
    }
}
