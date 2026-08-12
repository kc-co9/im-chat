package com.co.kc.imchat.service.message.infrastructure.mybatis.entity;

import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @Test
    void newEntityDefaultsToActiveLogicDeleteValue() {
        BaseEntity entity = new BaseEntity();

        assertThat(entity.getIsDeleted()).isZero();
    }
}
