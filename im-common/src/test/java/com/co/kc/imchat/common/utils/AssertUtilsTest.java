package com.co.kc.imchat.common.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssertUtilsTest {

    @Test
    void argNotNullRejectsNullArgument() {
        assertThatThrownBy(() -> AssertUtils.argNotNull("userId is null", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is null");
    }

    @Test
    void argNotBlankRejectsBlankString() {
        assertThatThrownBy(() -> AssertUtils.argNotBlank("帐号为空", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("帐号为空");
    }

    @Test
    void argNotEmptyRejectsEmptyCollection() {
        assertThatThrownBy(() -> AssertUtils.argNotEmpty("群组成员不能为空", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("群组成员不能为空");
    }

    @Test
    void argTrueRejectsFalseExpression() {
        assertThatThrownBy(() -> AssertUtils.argTrue("userId is less than 0", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is less than 0");
    }

    @Test
    void domainPropNotNullRejectsNullProperty() {
        assertThatThrownBy(() -> AssertUtils.domainPropNotNull("用户ID不能为空", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("用户ID不能为空");
    }

    @Test
    void domainPropNotBlankRejectsBlankProperty() {
        assertThatThrownBy(() -> AssertUtils.domainPropNotBlank("群组名称不能为空", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("群组名称不能为空");
    }

    @Test
    void domainPropNotEmptyRejectsEmptyCollectionProperty() {
        assertThatThrownBy(() -> AssertUtils.domainPropNotEmpty("群组成员不能为空", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("群组成员不能为空");
    }
}
