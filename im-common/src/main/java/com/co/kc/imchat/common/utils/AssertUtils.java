package com.co.kc.imchat.common.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Objects;

public class AssertUtils {
    public static void argNotNull(String message, Object object) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void argNotBlank(String message, String object) {
        if (StringUtils.isBlank(object)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void argNotEmpty(String message, Collection<?> object) {
        if (CollectionUtils.isEmpty(object)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void allArgNotNull(String message, Object... objects) {
        for (Object object : objects) {
            argNotNull(message, object);
        }
    }

    public static void anyArgNotNull(String message, Object... objects) {
        boolean hasNotNull = false;
        for (Object object : objects) {
            if (object != null) {
                hasNotNull = true;
                break;
            }
        }
        if (!hasNotNull) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void argTrue(String message, Boolean expected) {
        if (!Boolean.TRUE.equals(expected)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void domainPropEquals(String message, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(message);
        }
    }

    public static void domainPropNotNull(String message, Object object) {
        if (object == null) {
            throw new IllegalStateException(message);
        }
    }

    public static void domainPropNotBlank(String message, String object) {
        if (StringUtils.isBlank(object)) {
            throw new IllegalStateException(message);
        }
    }

    public static void domainPropNotEmpty(String message, Collection<?> object) {
        if (CollectionUtils.isEmpty(object)) {
            throw new IllegalStateException(message);
        }
    }

    public static void allDomainPropNotNull(String message, Object... objects) {
        for (Object object : objects) {
            domainPropNotNull(message, object);
        }
    }

    public static void domainPropTrue(String message, Boolean expected) {
        if (!Boolean.TRUE.equals(expected)) {
            throw new IllegalStateException(message);
        }
    }
}
