package com.co.kc.imchat.transformer.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatName;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImUserChatDescriptor;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ImChatAppTransformerTest {

    @Test
    void chatItemContainsLastMessageAndSendTime() {
        LocalDateTime sendTime = LocalDateTime.of(2026, 5, 15, 18, 30);
        ImUserChatDescriptor descriptor = new ImUserChatDescriptor();
        descriptor.setChatId(new ImChatId(101L));
        descriptor.setChatName(new ImChatName("alice"));
        descriptor.setChatType(ImChatType.PRIVATE);
        descriptor.setChatLastMessage(ImPrivateInboxMessage.builder()
                .id(new ImMessageId(900L))
                .token(new ImMessageToken("token-900"))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .chatId(new ImChatId(101L))
                .userId(new UserId(1L))
                .senderId(new UserId(2L))
                .status(ImPrivateMessageStatus.SENT)
                .sendTime(sendTime)
                .build());

        ImChatItemDTO dto = ImChatAppTransformer.INSTANCE.imChatItemDtoFrom(descriptor);

        assertThat(dto.getLastMessageType()).isEqualTo(ImMessageTypeEnum.TEXT);
        assertThat(dto.getLastMessageContent()).isEqualTo("hello");
        assertThat(dto.getLastMessageTime()).isEqualTo(sendTime);
    }

    @Test
    void chatItemHidesRevokedLastMessageContent() {
        ImUserChatDescriptor descriptor = new ImUserChatDescriptor();
        descriptor.setChatId(new ImChatId(101L));
        descriptor.setChatName(new ImChatName("alice"));
        descriptor.setChatType(ImChatType.PRIVATE);
        descriptor.setChatLastMessage(ImPrivateInboxMessage.builder()
                .id(new ImMessageId(900L))
                .token(new ImMessageToken("token-900"))
                .content(new ImMessageContent(ImMessageType.TEXT, "secret"))
                .chatId(new ImChatId(101L))
                .userId(new UserId(1L))
                .senderId(new UserId(2L))
                .status(ImPrivateMessageStatus.REVOKED)
                .sendTime(LocalDateTime.now())
                .build());

        ImChatItemDTO dto = ImChatAppTransformer.INSTANCE.imChatItemDtoFrom(descriptor);

        assertThat(dto.getLastMessageContent()).isNull();
    }

    @Test
    void chatItemAllowsEmptyLastMessage() {
        ImUserChatDescriptor descriptor = new ImUserChatDescriptor();
        descriptor.setChatId(new ImChatId(101L));
        descriptor.setChatName(new ImChatName("alice"));
        descriptor.setChatType(ImChatType.PRIVATE);

        ImChatItemDTO dto = ImChatAppTransformer.INSTANCE.imChatItemDtoFrom(descriptor);

        assertThat(dto.getLastMessageType()).isNull();
        assertThat(dto.getLastMessageContent()).isNull();
        assertThat(dto.getLastMessageTime()).isNull();
    }
}
