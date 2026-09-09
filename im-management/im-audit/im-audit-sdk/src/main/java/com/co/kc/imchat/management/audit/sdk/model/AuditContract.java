package com.co.kc.imchat.management.audit.sdk.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 审计传输契约的统一容量校验。 */
final class AuditContract {
    static final int ID_LENGTH = 128;
    static final int ACTION_LENGTH = 128;
    static final int TYPE_LENGTH = 64;
    static final int NAME_LENGTH = 128;
    static final int ERROR_CODE_LENGTH = 128;
    static final int DESCRIPTION_LENGTH = 512;
    static final int CLIENT_ADDRESS_LENGTH = 64;
    static final int USER_AGENT_LENGTH = 512;
    static final int TRACE_ID_LENGTH = 128;

    private AuditContract() {
    }

    static void validateRequired(String name, String value, int maximumLength) {
        AssertUtils.argNotBlank(name + " must not be blank", value);
        validateLength(name, value, maximumLength);
    }

    static void validateOptional(String name, String value, int maximumLength) {
        if (value == null) {
            return;
        }
        validateRequired(name, value, maximumLength);
    }

    private static void validateLength(String name, String value, int maximumLength) {
        AssertUtils.argTrue(
                name + " must contain at most " + maximumLength + " characters",
                value.length() <= maximumLength);
    }
}
