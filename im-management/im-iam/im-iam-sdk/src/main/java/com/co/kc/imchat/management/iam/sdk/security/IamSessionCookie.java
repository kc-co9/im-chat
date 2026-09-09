package com.co.kc.imchat.management.iam.sdk.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/** 应用 BFF 会话 Cookie 的统一读写边界。 */
public class IamSessionCookie {
    private final String name;
    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public IamSessionCookie(String name, boolean secure, String sameSite, Duration maxAge) {
        this.name = name;
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAge = maxAge;
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public void write(String sessionId, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(sessionId, maxAge).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(age)
                .build();
    }
}
