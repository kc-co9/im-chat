package com.co.kc.imchat.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectUtilsTest {

    @Test
    void resolveGenericTypeArgumentsReturnsAllGenericTypes() {
        assertThat(ReflectUtils.resolveGenericTypeArguments(StringIntegerHandler.class, BaseHandler.class))
                .containsExactly(String.class, Integer.class);
    }

    @Test
    void resolveGenericTypeArgumentReturnsIndexedGenericType() {
        assertThat(ReflectUtils.resolveGenericTypeArgument(StringIntegerHandler.class, BaseHandler.class, 0))
                .isEqualTo(String.class);
        assertThat(ReflectUtils.resolveGenericTypeArgument(StringIntegerHandler.class, BaseHandler.class, 1))
                .isEqualTo(Integer.class);
        assertThat(ReflectUtils.resolveGenericTypeArgument(StringIntegerHandler.class, BaseHandler.class, 2))
                .isNull();
    }

    @Test
    void resolveGenericTypeArgumentsReturnsGenericTypesFromParentClass() {
        assertThat(ReflectUtils.resolveGenericTypeArguments(ChildHandler.class, BaseHandler.class))
                .containsExactly(String.class, Long.class);
    }

    @Test
    void resolveGenericTypeArgumentReturnsNullWhenTargetTypeNotFound() {
        assertThat(ReflectUtils.resolveGenericTypeArgument(StringIntegerHandler.class, OtherHandler.class, 0))
                .isNull();
    }

    private abstract static class BaseHandler<I, O> {
    }

    private static class StringIntegerHandler extends BaseHandler<String, Integer> {
    }

    private abstract static class ParentHandler extends BaseHandler<String, Long> {
    }

    private static class ChildHandler extends ParentHandler {
    }

    private abstract static class OtherHandler<T> {
    }
}
