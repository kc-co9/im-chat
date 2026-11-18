package com.co.kc.imchat.management.ops.cli.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * 单次前台登录的待完成授权会话；仅在校验回调后向换码流程交付授权码。
 */
public final class CliLoginSession {

    /** 防跨请求伪造的随机 state。 */
    private final String state;
    /** PKCE code verifier。 */
    private final String verifier;
    /** 本次登录固定的绝对截止时间。 */
    private final Instant deadline;
    /** 授权码是否已经消费。 */
    private boolean consumed;

    private CliLoginSession(String state, String verifier) {
        this.state = state;
        this.verifier = verifier;
        this.deadline = Instant.now().plusSeconds(120);
    }

    /** 创建一次性 state、PKCE verifier 和固定 120 秒截止时间。 */
    public static CliLoginSession start() {
        SecureRandom random = new SecureRandom();
        byte[] randomState = new byte[32];
        byte[] randomVerifier = new byte[32];
        random.nextBytes(randomState);
        random.nextBytes(randomVerifier);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return new CliLoginSession(encoder.encodeToString(randomState), encoder.encodeToString(randomVerifier));
    }

    /** 返回本次登录固定的绝对截止时间，调用方不得延长预算。 */
    public Instant deadline() {
        return deadline;
    }

    /** 返回授权请求使用的一次性 state。 */
    public String state() {
        return state;
    }

    /** 返回仅用于当前授权码交换的 PKCE verifier。 */
    public String verifier() {
        return verifier;
    }

    /** 返回 RFC 7636 S256 code challenge，不暴露 verifier。 */
    public String challenge() {
        // 按 RFC 7636 使用 SHA-256 生成 URL 安全的 code challenge。
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** 校验并消费一次性回调参数，拒绝重复、过期或跨会话授权码。 */
    public synchronized String acceptAuthorizationCode(String state, String code) {
        // 按顺序校验一次性、时效、state 和授权码，成功后立即消费会话。
        if (consumed) {
            throw new IllegalStateException("Authorization callback already consumed");
        }
        if (!Instant.now().isBefore(deadline)) {
            throw new IllegalStateException("Authorization login expired");
        }
        if (!this.state.equals(state)) {
            throw new IllegalArgumentException("Authorization state mismatch");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code is required");
        }
        consumed = true;
        return code;
    }
}
