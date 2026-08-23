package com.co.kc.imchat.gateway.ws.security.authentication;

import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * WS 认证管理器。
 * <p>
 * 负责把握手阶段提取到的 token 转换为网关内部的认证身份。
 */
public class WsAuthenticationManager {
    private final AccountService accountService;
    private final Executor authenticationExecutor;
    private final Duration timeout;

    public WsAuthenticationManager(AccountService accountService, Executor authenticationExecutor, Duration timeout) {
        this.accountService = accountService;
        this.authenticationExecutor = authenticationExecutor;
        this.timeout = timeout;
    }

    /**
     * 根据握手令牌认证用户身份。
     *
     * @param token 握手令牌
     * @return 认证身份；无法认证时返回 null
     */
    public CompletionStage<WsPrincipal> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> accountService.authenticate(new AccessTokenParams(token)), authenticationExecutor)
                .completeOnTimeout(null, timeout.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(WsAuthenticationManager::toPrincipal)
                .exceptionally(exception -> null);
    }

    private static WsPrincipal toPrincipal(SessionAuthDTO result) {
        if (result == null) {
            return null;
        }
        return new WsPrincipal(result.userId(), result.sessionVersion());
    }
}
