package com.co.kc.imchat.application.transformer;

import com.co.kc.imchat.application.transformer.ImChatAppTransformer;
import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.model.ImChatName;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.domain.message.model.ImMessageContent;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.application.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ImChatAppTransformerTest {

    @Test
    void chatItemContainsLastMessageAndSendTime() {
        LocalDateTime sendTime = LocalDateTime.of(2026, 5, 15, 18, 30);
        ImUserChatDescriptor descriptor = new ImUserChatDescriptor(
                new ImChatId(101L),
                new ImChatName("alice"),
                ImChatType.PRIVATE,
                ImPrivateInboxMessage.builder()
                        .id(new ImMessageId(900L))
                        .token(new ImMessageToken("token-900"))
                        .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                        .chatId(new ImChatId(101L))
                        .userId(new UserId(1L))
                        .senderId(new UserId(2L))
                        .status(ImPrivateMessageStatus.SENT)
                        .sendTime(sendTime)
                        .build(),
                null);

        ImChatItemDTO dto = ImChatAppTransformer.INSTANCE.imChatItemDtoFrom(descriptor);

        assertThat(dto.getLastMessageType()).isEqualTo(ImMessageTypeEnum.TEXT);
        assertThat(dto.getLastMessageContent()).isEqualTo("hello");
        assertThat(dto.getLastMessageTime()).isEqualTo(sendTime);
    }

    @Test
    void chatItemHidesRevokedLastMessageContent() {
        ImUserChatDescriptor descriptor = new ImUserChatDescriptor(
                new ImChatId(101L),
                new ImChatName("alice"),
                ImChatType.PRIVATE,
                ImPrivateInboxMessage.builder()
                        .id(new ImMessageId(900L))
                        .token(new ImMessageToken("token-900"))
                        .content(new ImMessageContent(ImMessageType.TEXT, "secret"))
                        .chatId(new ImChatId(101L))
                        .userId(new UserId(1L))
                        .senderId(new UserId(2L))
                        .status(ImPrivateMessageStatus.REVOKED)
                        .sendTime(LocalDateTime.now())
                        .build(),
                null);

        ImChatItemDTO dto = ImChatAppTransformer.INSTANCE.imChatItemDtoFrom(descriptor);

        assertThat(dto.getLastMessageContent()).isNull();
    }

    @Test
    void chatItemAllowsEmptyLastMessage() {
        ImUserChatDescriptor descriptor = new ImUserChatDescriptor(
                new ImChatId(101L),
                new ImChatName("alice"),
                ImChatType.PRIVATE,
                null,
                null);

        ImChatItemDTO dto = ImChatAppTransformer.INSTANCE.imChatItemDtoFrom(descriptor);

        assertThat(dto.getLastMessageType()).isNull();
        assertThat(dto.getLastMessageContent()).isNull();
        assertThat(dto.getLastMessageTime()).isNull();
    }
}
