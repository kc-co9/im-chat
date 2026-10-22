package com.co.kc.imchat.plugin.bolt.exception;

/**
 * 认证调用的保守失败分类；只有明确的目标传输失败可作为不可达证据。
 */
public final class BoltInvocationException extends IllegalStateException {
    public enum Failure {TRANSPORT_UNAVAILABLE, AUTHENTICATION, PROTOCOL, LOCAL}

    private final Failure failure;

    public BoltInvocationException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
