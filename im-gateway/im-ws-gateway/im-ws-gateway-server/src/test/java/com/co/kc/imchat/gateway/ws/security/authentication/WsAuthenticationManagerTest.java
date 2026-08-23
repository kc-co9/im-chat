package com.co.kc.imchat.gateway.ws.security.authentication;

import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WsAuthenticationManagerTest {

    @Test
    void authenticatesOnlyOnlineAccessTokenAndCarriesSessionVersion() {
        AccountService accountService = mock(AccountService.class);
        when(accountService.authenticate(new AccessTokenParams("access-token")))
                .thenReturn(new SessionAuthDTO(
                        42L, "session-v1", Instant.parse("2026-08-16T12:00:00Z")));
        when(accountService.authenticate(new AccessTokenParams("refresh-token")))
                .thenThrow(new com.co.kc.imchat.common.exception.AuthException("Access Token 或会话无效"));
        WsAuthenticationManager manager = new WsAuthenticationManager(
                accountService, Runnable::run, Duration.ofSeconds(1));

        assertThat(manager.authenticate("access-token").toCompletableFuture().join())
                .isEqualTo(new WsPrincipal(42L, "session-v1"));
        assertThat(manager.authenticate("refresh-token").toCompletableFuture().join()).isNull();
    }

    @Test
    void accountFailureAndTimeoutFailClosed() {
        AccountService failingService = mock(AccountService.class);
        when(failingService.authenticate(new AccessTokenParams("error-token")))
                .thenThrow(new IllegalStateException("account unavailable"));
        WsAuthenticationManager failingManager = new WsAuthenticationManager(
                failingService, Runnable::run, Duration.ofSeconds(1));

        assertThat(failingManager.authenticate("error-token").toCompletableFuture().join()).isNull();

        AccountService blockingService = mock(AccountService.class);
        CountDownLatch blockedUntilCancelled = new CountDownLatch(1);
        when(blockingService.authenticate(new AccessTokenParams("slow-token")))
                .thenAnswer(invocation -> {
                    blockedUntilCancelled.await();
                    return new SessionAuthDTO(
                            42L, "session-v1", Instant.parse("2026-08-16T12:00:00Z"));
                });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            WsAuthenticationManager timeoutManager = new WsAuthenticationManager(
                    blockingService, executor, Duration.ofMillis(20));
            assertThat(timeoutManager.authenticate("slow-token").toCompletableFuture().join()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }
}
