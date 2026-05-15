package com.co.kc.imchat.transformer.db;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbGroupImMessageStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ImMessageDbTransformerTest {

    @Test
    void groupReceiverMessageKeepsSentStateWhenPersisted() {
        ImGroupInboxMessage message = ImGroupInboxMessage.builder()
                .id(new ImMessageId(900L))
                .token(new ImMessageToken("token-1"))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .groupId(new GroupId(1001L))
                .chatId(new ImChatId(102L))
                .userId(new UserId(2L))
                .senderId(new UserId(1L))
                .status(ImGroupMessageStatus.SENT)
                .sendTime(LocalDateTime.now())
                .build();

        DbImGroupInboxMessage row = ImMessageDbTransformer.INSTANCE.dbImGroupInboxMessageFrom(message);

        assertThat(row.getStatus()).isEqualTo(DbGroupImMessageStatus.SENT);
        assertThat(row.getReceiveTime()).isNull();
        assertThat(row.getReadTime()).isNull();
    }
}
