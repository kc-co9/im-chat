package com.co.kc.imchat.gateway.http.security.authentication;

import com.co.kc.imchat.gateway.http.security.filter.UserContextHeaderSanitizingFilter;
import com.co.kc.imchat.plugin.session.context.UserContextHeaders;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class SecurityComponentTest {

    @Test
    void converterReadsBearerToken() {
        AuthenticationConverter converter = new AuthenticationConverter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/social/friend/friendList")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"));

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication).isInstanceOf(AuthenticationToken.class);
        assertThat(((AuthenticationToken) authentication).getToken()).isEqualTo("valid-token");
    }

    @Test
    void managerAuthenticatesTokenWithUserId() {
        AccountService accountService = mock(AccountService.class);
        when(accountService.authenticate(new AccessTokenParams("valid-token")))
                .thenReturn(new SessionAuthDTO(
                        42L, "session-v1", Instant.parse("2026-08-14T12:00:00Z")));
        AuthenticationManager manager = manager(accountService);

        Authentication authentication = manager.authenticate(new AuthenticationToken("valid-token")).block();

        assertThat(authentication).isInstanceOf(AuthenticationToken.class);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(((AuthenticationToken) authentication).getUserId()).isEqualTo(42L);
        assertThat(((AuthenticationToken) authentication).getSessionVersion()).isEqualTo("session-v1");
    }

    @Test
    void managerRejectsAuthenticationExceptionFromAccount() {
        AccountService accountService = mock(AccountService.class);
        when(accountService.authenticate(new AccessTokenParams("bad-token")))
                .thenThrow(new com.co.kc.imchat.common.exception.AuthException("Access Token 或会话无效"));
        AuthenticationManager manager = manager(accountService);

        assertThatThrownBy(() -> manager.authenticate(new AuthenticationToken("bad-token")).block())
                .hasMessageContaining("Invalid token");
    }

    @Test
    void managerFailsClosedWhenAccountValidationThrows() {
        AccountService accountService = mock(AccountService.class);
        when(accountService.authenticate(new AccessTokenParams("error-token")))
                .thenThrow(new IllegalStateException("account unavailable"));
        AuthenticationManager manager = manager(accountService);

        assertThatThrownBy(() -> manager.authenticate(new AuthenticationToken("error-token")).block())
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
                .hasMessageContaining("Invalid token");
    }

    @Test
    void successHandlerAddsTrustedUserHeaderAndRemovesSpoofedHeader() {
        AuthenticationSuccessHandler successHandler = new AuthenticationSuccessHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/social/friend/friendList")
                .header(UserContextHeaders.USER_ID, "999")
                .header(UserContextHeaders.SESSION_VERSION, "spoofed"));
        AtomicReference<ServerWebExchange> downstreamExchange = new AtomicReference<>();
        WebFilterChain chain = filteredExchange -> {
            downstreamExchange.set(filteredExchange);
            return Mono.empty();
        };

        successHandler.onAuthenticationSuccess(
                new WebFilterExchange(exchange, chain),
                new AuthenticationToken("valid-token", 42L, "session-v1")).block();

        assertThat(downstreamExchange.get().getRequest().getHeaders().getFirst(UserContextHeaders.USER_ID))
                .isEqualTo("42");
        assertThat(downstreamExchange.get().getRequest().getHeaders().getFirst(UserContextHeaders.SESSION_VERSION))
                .isEqualTo("session-v1");
    }

    @Test
    void sanitizingFilterRemovesExternalUserHeader() {
        UserContextHeaderSanitizingFilter filter = new UserContextHeaderSanitizingFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/account/user/signIn")
                .header(UserContextHeaders.USER_ID, "999")
                .header(UserContextHeaders.SESSION_VERSION, "spoofed"));
        AtomicReference<ServerWebExchange> downstreamExchange = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            downstreamExchange.set(filteredExchange);
            return Mono.empty();
        }).block();

        assertThat(downstreamExchange.get().getRequest().getHeaders().containsKey(UserContextHeaders.USER_ID))
                .isFalse();
        assertThat(downstreamExchange.get().getRequest().getHeaders().containsKey(UserContextHeaders.SESSION_VERSION))
                .isFalse();
    }

    @Test
    void blockingAccountValidationRunsOffCallingThreadAndTimesOutClosed() {
        AccountService accountService = mock(AccountService.class);
        AtomicReference<String> validationThread = new AtomicReference<>();
        CountDownLatch blockedUntilCancelled = new CountDownLatch(1);
        when(accountService.authenticate(new AccessTokenParams("slow-token")))
                .thenAnswer(invocation -> {
                    validationThread.set(Thread.currentThread().getName());
                    try {
                        blockedUntilCancelled.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return new SessionAuthDTO(
                            42L, "session-v1", Instant.parse("2026-08-14T12:00:00Z"));
                });
        Scheduler scheduler = Schedulers.newBoundedElastic(1, 10, "test-account-authentication");
        try {
            AuthenticationManager manager = new AuthenticationManager(
                    accountService, scheduler, Duration.ofMillis(20));

            assertThatThrownBy(() -> manager.authenticate(new AuthenticationToken("slow-token")).block())
                    .hasMessageContaining("Invalid token");
            assertThat(validationThread.get()).startsWith("test-account-authentication-");
            assertThat(validationThread.get()).isNotEqualTo(Thread.currentThread().getName());
        } finally {
            scheduler.dispose();
        }
    }

    private AuthenticationManager manager(AccountService accountService) {
        return new AuthenticationManager(accountService, Schedulers.immediate(), Duration.ofSeconds(1));
    }

}
