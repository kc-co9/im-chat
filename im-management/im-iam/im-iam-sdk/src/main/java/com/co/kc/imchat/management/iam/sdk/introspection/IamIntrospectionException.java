package com.co.kc.imchat.management.iam.sdk.introspection;

/** Introspection 调用失败，明确区分可用性故障与认证拒绝。 */
public class IamIntrospectionException extends RuntimeException {
    private final boolean availabilityFailure;

    public IamIntrospectionException(
            String message,
            boolean availabilityFailure,
            Throwable cause
    ) {
        super(message, cause);
        this.availabilityFailure = availabilityFailure;
    }

    public boolean isAvailabilityFailure() {
        return availabilityFailure;
    }
}
