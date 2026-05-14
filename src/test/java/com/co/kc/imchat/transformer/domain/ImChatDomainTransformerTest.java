package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImGroupStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImChatDomainTransformerTest {

    @Test
    void imGroupFromRejectsUnknownGroupStatus() {
        DbImGroup row = new DbImGroup();
        row.setGroupId(1001L);
        row.setOwnerId(1L);
        row.setName("group");
        row.setStatus(DbImGroupStatus.NONE);

        assertThatThrownBy(() -> ImChatDomainTransformer.INSTANCE.imGroupFrom(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("群状态不能为未知");
    }

    @Test
    void imGroupFromRejectsNullGroupStatus() {
        DbImGroup row = new DbImGroup();
        row.setGroupId(1001L);
        row.setOwnerId(1L);
        row.setName("group");

        assertThatThrownBy(() -> ImChatDomainTransformer.INSTANCE.imGroupFrom(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("群状态不能为未知");
    }
}
