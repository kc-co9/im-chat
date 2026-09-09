package com.co.kc.imchat.management.iam.sdk.oauth;

import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.common.utils.HashUtils;
import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;

/** 编排 PKCE 登录回调、服务端 Token 会话、提前刷新与当前应用退出。 */
@RequiredArgsConstructor
public class IamAuthorizedSessionService {
    private static final int IDENTIFIER_BYTES = 32;
    private static final Duration REFRESH_AHEAD = Duration.ofMinutes(1);

    private final IamAuthorizationClient authorizationClient;
    private final IamAuthorizationRequestRepository authorizationRequests;
    private final IamApplicationSessionRepository sessions;

    public URI beginAuthorization(String continuePath) {
        String state = GeneratorUtils.nextRandomId(IDENTIFIER_BYTES);
        String verifier = GeneratorUtils.nextRandomId(64);
        authorizationRequests.save(
                state,
                new IamAuthorizationRequest(verifier, safeContinuePath(continuePath)));
        return authorizationClient.authorizationUri(state, challenge(verifier));
    }

    @Observed(name = "im.iam.authorization.code.exchange")
    public IamAuthorizationCompletion completeAuthorization(String state, String code) {
        IamAuthorizationRequest request = authorizationRequests.consume(state)
                .orElseThrow(() -> new IamOAuthException(
                        "IAM authorization state is invalid or already used", null));
        IamTokenSet tokens = authorizationClient.exchange(code, request.codeVerifier());
        Instant now = Instant.now();
        IamApplicationSession session = new IamApplicationSession(
                GeneratorUtils.nextRandomId(IDENTIFIER_BYTES),
                tokens,
                GeneratorUtils.nextRandomId(IDENTIFIER_BYTES),
                now,
                now);
        sessions.save(session);
        return new IamAuthorizationCompletion(session, request.continuePath());
    }

    @Observed(name = "im.iam.application.session.resolve")
    public Optional<IamApplicationSession> current(String sessionId) {
        return sessions.find(sessionId).map(this::refreshIfNecessary);
    }

    @Observed(name = "im.iam.application.logout")
    public void logout(String sessionId) {
        sessions.find(sessionId).ifPresent(session -> {
            authorizationClient.revoke(session.tokens().accessToken());
            authorizationClient.revoke(session.tokens().refreshToken());
        });
        sessions.remove(sessionId);
    }

    public URI platformLogoutUri(String sessionId) {
        logout(sessionId);
        return authorizationClient.platformLogoutUri();
    }

    private IamApplicationSession refreshIfNecessary(IamApplicationSession session) {
        Instant now = Instant.now();
        if (session.tokens().accessTokenExpiresAt().isAfter(now.plus(REFRESH_AHEAD))) {
            return session;
        }
        if (!session.tokens().refreshTokenExpiresAt().isAfter(now)) {
            sessions.remove(session.sessionId());
            throw new IamOAuthException("IAM Refresh Token expired", null);
        }
        IamTokenSet tokens;
        try {
            tokens = authorizationClient.refresh(session.tokens().refreshToken());
        } catch (IamInvalidRefreshTokenException exception) {
            sessions.remove(session.sessionId());
            throw exception;
        }
        IamApplicationSession refreshed = session.rotate(tokens, now);
        if (sessions.replace(session, refreshed)) {
            return refreshed;
        }
        return sessions.find(session.sessionId())
                .orElseThrow(() -> new IamOAuthException(
                        "IAM application Session changed during refresh", null));
    }

    private String challenge(String verifier) {
        byte[] digest = HashUtils.sha256(verifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String safeContinuePath(String continuePath) {
        if (continuePath == null
                || !continuePath.startsWith("/")
                || continuePath.startsWith("//")
                || continuePath.contains("\r")
                || continuePath.contains("\n")) {
            return "/";
        }
        return continuePath;
    }
}
