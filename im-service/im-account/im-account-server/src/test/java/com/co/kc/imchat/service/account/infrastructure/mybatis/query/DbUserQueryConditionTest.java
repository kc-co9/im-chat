package com.co.kc.imchat.service.account.infrastructure.mybatis.query;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DbUserQueryConditionTest {

    @Test
    void treatsBlankTextAsAbsent() {
        DbUserQueryCondition condition = new DbUserQueryCondition(
                Optional.empty(), Optional.of("  "), Optional.of(""), Optional.empty());

        assertThat(condition.username()).isEmpty();
        assertThat(condition.email()).isEmpty();
    }

    @Test
    void rejectsNullOptionalContainer() {
        assertThatThrownBy(() -> new DbUserQueryCondition(
                null, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user query condition options must not be null");
    }
}
