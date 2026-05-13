package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImGroupMessageTransmission;
import com.co.kc.imchat.domain.message.ImMessageRecipient;
import com.co.kc.imchat.domain.message.ImMessageSender;
import com.co.kc.imchat.domain.message.ImOutboundMessage;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ImMessageServiceTest {

    @Test
    void transmitGroupMessageBuildsInboxMessagesAndUpdatesChats() {
        ImMessageService service = new ImMessageService();
        UserId senderId = new UserId(1L);
        ImGroupChat senderChat = groupChat(101L, 1001L, 1L);
        ImGroupChat receiverChat = groupChat(102L, 1001L, 2L);

        ImGroupMessageTransmission transmission = service.transmitGroupMessage(
                new ImOutboundMessage(
                        new ImMessageId(900L),
                        new ImMessageToken("token-1"),
                        new ImMessageContent(ImMessageType.TEXT, "hello")),
                new ImMessageSender(senderChat, senderId),
                Arrays.asList(
                        new ImMessageRecipient(senderChat, true),
                        new ImMessageRecipient(receiverChat, false)));

        assertThat(transmission.getInboxMessages()).hasSize(2);
        ImGroupInboxMessage senderMessage = transmission.getSenderMessage(senderId);
        assertThat(senderMessage.getChatId().getValue()).isEqualTo(101L);
        assertThat(senderMessage.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(senderMessage.getReadTime()).isNotNull();
        assertThat(senderMessage.getReceivedTime()).isNull();
        ImGroupInboxMessage receiverMessage = transmission.getInboxMessages().stream()
                .filter(message -> message.getUserId().equals(new UserId(2L)))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.RECEIVED);
        assertThat(receiverMessage.getReceivedTime()).isNotNull();
        assertThat(senderChat.getUnreadMessageCount()).isZero();
        assertThat(senderChat.getReadMessageId().getValue()).isEqualTo(900L);
        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(receiverChat.getReadMessageId()).isNull();
        assertThat(transmission.getGroupChats()).containsExactly(senderChat, receiverChat);
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId) {
        return ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .groupId(new ImGroupId(groupId))
                .userId(new UserId(userId))
                .type(ImChatType.GROUP)
                .unreadMessageCount(0)
                .build();
    }
}
