package com.co.kc.imchat.infrastructure.mybatis.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @Test
    void newEntityDefaultsToActiveLogicDeleteValue() {
        BaseEntity entity = new BaseEntity();

        assertThat(entity.getIsDeleted()).isZero();
    }
}
