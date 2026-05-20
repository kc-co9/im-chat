package com.co.kc.imchat.transformer.domain;

import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupStatus;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroup;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatStatus;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImGroupStatus;
import com.co.kc.imchat.infrastructure.transformer.domain.ImChatDomainTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImChatDomainTransformerTest {

    @Test
    void imGroupFromMapsMemberCount() {
        DbImGroup row = new DbImGroup();
        row.setId(1L);
        row.setGroupId(1001L);
        row.setOwnerId(1L);
        row.setName("group");
        row.setMemberCount(3);
        row.setStatus(DbImGroupStatus.NORMAL);

        Group group = ImChatDomainTransformer.INSTANCE.imGroupFrom(row);

        assertThat(group.getMemberCount().getValue()).isEqualTo(3);
        assertThat(group.getStatus()).isEqualTo(GroupStatus.ACTIVE);
    }

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

    @Test
    void privateChatFromMapsZeroMessageIdsToNull() {
        DbImPrivateChat row = new DbImPrivateChat();
        row.setChatId(101L);
        row.setUserId(1L);
        row.setPeerUserId(2L);
        row.setLastMessageId(0L);
        row.setReadMessageId(0L);
        row.setUnreadMessageCount(0);
        row.setStatus(DbImChatStatus.NORMAL);

        ImPrivateChat chat = ImChatDomainTransformer.INSTANCE.imPrivateChatFrom(row);

        assertThat(chat.getLastMessageId()).isNull();
        assertThat(chat.getReadMessageId()).isNull();
    }

    @Test
    void groupChatFromMapsZeroMessageIdsToNull() {
        DbImGroupChat row = new DbImGroupChat();
        row.setChatId(101L);
        row.setGroupId(1001L);
        row.setUserId(1L);
        row.setLastMessageId(0L);
        row.setReadMessageId(0L);
        row.setUnreadMessageCount(0);
        row.setStatus(DbImChatStatus.NORMAL);

        ImGroupChat chat = ImChatDomainTransformer.INSTANCE.imGroupChatFrom(row);

        assertThat(chat.getLastMessageId()).isNull();
        assertThat(chat.getReadMessageId()).isNull();
    }
}
