package com.co.kc.imchat.gateway.ws.config;

import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import com.co.kc.imchat.service.account.facade.AccountService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AccountAuthenticationConfig {
    private static final Duration AUTHENTICATION_TIMEOUT = Duration.ofSeconds(1);

    @DubboReference(interfaceClass = AccountService.class, version = "1.0.0", timeout = 800, retries = 0)
    private AccountService accountService;

    @Bean(name = "authExecutor", destroyMethod = "shutdownNow")
    public ExecutorService authExecutor() {
        AtomicInteger threadNumber = new AtomicInteger();
        return new ThreadPoolExecutor(
                4, 8, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1_000),
                runnable -> new Thread(runnable, "ws-account-authentication-" + threadNumber.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public WsAuthenticationManager wsAuthenticationManager(@Qualifier("authExecutor") Executor authenticationExecutor) {
        return new WsAuthenticationManager(accountService, authenticationExecutor, AUTHENTICATION_TIMEOUT);
    }
}
