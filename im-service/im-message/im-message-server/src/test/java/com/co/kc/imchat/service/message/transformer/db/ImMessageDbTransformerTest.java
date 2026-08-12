package com.co.kc.imchat.service.message.transformer.db;

import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.enums.DbGroupImMessageStatus;
import com.co.kc.imchat.service.message.transformer.db.ImMessageDbTransformer;
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
