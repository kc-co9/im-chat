package com.co.kc.imchat.service.message.transformer.domain;

import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbImChatStatus;
import com.co.kc.imchat.service.message.transformer.domain.ImChatDomainTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImChatDomainTransformerTest {

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
