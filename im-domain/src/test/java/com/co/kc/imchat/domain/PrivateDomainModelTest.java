package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.message.model.ImMessageContent;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.user.model.UserId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateDomainModelTest {

    @Test
    void privateInboxMessageKeepsInboxAndPeerUsers() {
        ImPrivateInboxMessage message = new ImPrivateInboxMessage();
        message.setId(new ImMessageId(200L));
        message.setToken(new ImMessageToken("token"));
        message.setContent(new ImMessageContent(ImMessageType.TEXT, "hello"));
        message.setSenderId(new UserId(1L));
        message.setUserId(new UserId(1L));
        message.setChatId(new ImChatId(99L));
        message.setStatus(ImPrivateMessageStatus.SENT);
        message.setSendTime(LocalDateTime.now());

        assertThat(message.getSenderId().getValue()).isEqualTo(1L);
        assertThat(message.getUserId().getValue()).isEqualTo(1L);
        assertThat(message.getStatus()).isEqualTo(ImPrivateMessageStatus.SENT);
    }

    @Test
    void privateInboxMessageReceiveIsIdempotent() {
        ImPrivateInboxMessage message = new ImPrivateInboxMessage();
        message.setId(new ImMessageId(200L));
        message.setToken(new ImMessageToken("token"));
        message.setContent(new ImMessageContent(ImMessageType.TEXT, "hello"));
        message.setSenderId(new UserId(1L));
        message.setUserId(new UserId(2L));
        message.setChatId(new ImChatId(99L));
        message.setStatus(ImPrivateMessageStatus.RECEIVED);
        message.setSendTime(LocalDateTime.now());

        message.receive(new UserId(2L));

        assertThat(message.getStatus()).isEqualTo(ImPrivateMessageStatus.RECEIVED);
        assertThat(message.getReceivedTime()).isNotNull();
    }

    @Test
    void privateInboxMessageReadIsIdempotent() {
        ImPrivateInboxMessage message = new ImPrivateInboxMessage();
        message.setId(new ImMessageId(200L));
        message.setToken(new ImMessageToken("token"));
        message.setContent(new ImMessageContent(ImMessageType.TEXT, "hello"));
        message.setSenderId(new UserId(1L));
        message.setUserId(new UserId(2L));
        message.setChatId(new ImChatId(99L));
        message.setStatus(ImPrivateMessageStatus.READ);
        message.setSendTime(LocalDateTime.now());
        message.setReadTime(LocalDateTime.now());

        message.read(new UserId(2L));

        assertThat(message.getStatus()).isEqualTo(ImPrivateMessageStatus.READ);
        assertThat(message.getReadTime()).isNotNull();
    }

    @Test
    void privateInboxMessageKeepsReadWhenReceiveAfterRead() {
        ImPrivateInboxMessage message = new ImPrivateInboxMessage();
        message.setId(new ImMessageId(200L));
        message.setToken(new ImMessageToken("token"));
        message.setContent(new ImMessageContent(ImMessageType.TEXT, "hello"));
        message.setSenderId(new UserId(1L));
        message.setUserId(new UserId(2L));
        message.setChatId(new ImChatId(99L));
        message.setStatus(ImPrivateMessageStatus.READ);
        message.setSendTime(LocalDateTime.now());
        message.setReadTime(LocalDateTime.now());

        message.receive(new UserId(2L));

        assertThat(message.getStatus()).isEqualTo(ImPrivateMessageStatus.READ);
        assertThat(message.getReceivedTime()).isNotNull();
    }
}
