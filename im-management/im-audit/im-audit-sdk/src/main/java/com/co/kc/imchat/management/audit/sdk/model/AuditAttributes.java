package com.co.kc.imchat.management.audit.sdk.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 显式声明、不可变且有容量上限的审计扩展属性。 */
public record AuditAttributes(Map<String, String> values) implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAXIMUM_ENTRIES = 20;
    private static final int MAXIMUM_KEY_LENGTH = 64;
    private static final int MAXIMUM_VALUE_LENGTH = 512;
    private static final int MAXIMUM_ENCODED_BYTES = 4_096;
    public AuditAttributes {
        AssertUtils.argNotNull("audit attributes must not be null", values);
        AssertUtils.argTrue(
                "audit attributes must contain at most 20 entries",
                values.size() <= MAXIMUM_ENTRIES);
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        int encodedBytes = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            AuditContract.validateRequired(
                    "audit attribute key",
                    key,
                    MAXIMUM_KEY_LENGTH);
            AssertUtils.argNotNull("audit attribute value must not be null", value);
            AssertUtils.argTrue(
                    "audit attribute value must contain at most 512 characters",
                    value.length() <= MAXIMUM_VALUE_LENGTH);
            encodedBytes += key.getBytes(StandardCharsets.UTF_8).length;
            encodedBytes += value.getBytes(StandardCharsets.UTF_8).length;
            copy.put(key, value);
        }
        AssertUtils.argTrue(
                "audit attributes must contain at most 4096 UTF-8 bytes",
                encodedBytes <= MAXIMUM_ENCODED_BYTES);
        values = Collections.unmodifiableMap(copy);
    }

}
