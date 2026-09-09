package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.time.Duration;
import java.time.Instant;

/** 将 CSRF Token 绑定到 Redis BFF 会话，并通过可读 Cookie 交给同源前端。 */
@RequiredArgsConstructor
public class IamCsrfTokenRepository implements CsrfTokenRepository {
    public static final String HEADER_NAME = "X-XSRF-TOKEN";
    public static final String COOKIE_NAME = "XSRF-TOKEN";
    private static final String PARAMETER_NAME = "_csrf";
    private static final int TOKEN_BYTES = 32;

    private final IamApplicationSessionRepository sessionRepository;
    private final IamSessionCookie sessionCookie;
    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return token(GeneratorUtils.nextRandomId(TOKEN_BYTES));
    }

    @Override
    public void saveToken(
            CsrfToken csrfToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        sessionCookie.read(request).flatMap(sessionRepository::find).ifPresent(session -> {
            if (csrfToken == null) {
                return;
            }
            sessionRepository.save(session.withCsrfToken(
                    csrfToken.getToken(), Instant.now()));
        });
        ResponseCookie cookie = ResponseCookie.from(
                        COOKIE_NAME, csrfToken == null ? "" : csrfToken.getToken())
                .httpOnly(false)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(csrfToken == null ? Duration.ZERO : maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void publish(String csrfToken, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, csrfToken)
                .httpOnly(false)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(false)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return sessionCookie.read(request)
                .flatMap(sessionRepository::find)
                .map(IamApplicationSession::csrfToken)
                .map(this::token)
                .orElse(null);
    }

    private CsrfToken token(String value) {
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, value);
    }
}
