package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImMessageType;
import com.co.kc.imchat.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupDomainModelTest {

    @Test
    void groupChatReceivesUnreadMessageWhenUserIsNotChatting() {
        ImGroupChat chat = ImGroupChat.builder()
                .id(new ImChatId(101L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(2L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(0)
                .build();
        ImGroupInboxMessage message = groupMessage(900L, 101L, 1001L, 2L, 1L);

        chat.receiveLatestMessage(message, false);

        assertThat(chat.getLastMessageId().getValue()).isEqualTo(900L);
        assertThat(chat.getReadMessageId()).isNull();
        assertThat(chat.getUnreadMessageCount()).isEqualTo(1);
    }

    @Test
    void groupChatReadsMessageImmediatelyWhenUserIsChatting() {
        ImGroupChat chat = ImGroupChat.builder()
                .id(new ImChatId(101L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(2L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(0)
                .build();
        ImGroupInboxMessage message = groupMessage(900L, 101L, 1001L, 2L, 1L);

        chat.receiveLatestMessage(message, true);

        assertThat(chat.getLastMessageId().getValue()).isEqualTo(900L);
        assertThat(chat.getReadMessageId().getValue()).isEqualTo(900L);
        assertThat(chat.getUnreadMessageCount()).isZero();
        assertThat(chat.getReadTime()).isNotNull();
    }

    @Test
    void groupChatReadsToLatestMessage() {
        ImGroupChat chat = ImGroupChat.builder()
                .id(new ImChatId(101L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(2L))
                .type(ImChatType.GROUP)
                .lastMessageId(new ImMessageId(900L))
                .unreadMessageCount(3)
                .build();

        chat.readToLatest();

        assertThat(chat.getReadMessageId().getValue()).isEqualTo(900L);
        assertThat(chat.getUnreadMessageCount()).isZero();
        assertThat(chat.getReadTime()).isNotNull();
    }

    @Test
    void groupInboxMessageCanBeReadByInboxOwner() {
        ImGroupInboxMessage message = groupMessage(900L, 101L, 1001L, 2L, 1L);

        message.read(new UserId(2L));

        assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(message.getReadTime()).isNotNull();
        assertThat(message.getReceivedTime()).isNotNull();
    }

    @Test
    void groupInboxMessageReceiveIsIdempotent() {
        ImGroupInboxMessage message = groupMessage(900L, 101L, 1001L, 2L, 1L);

        message.receive(new UserId(2L));

        assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.RECEIVED);
        assertThat(message.getReceivedTime()).isNotNull();
    }

    @Test
    void groupInboxMessageReadIsIdempotent() {
        ImGroupInboxMessage message = groupMessage(900L, 101L, 1001L, 2L, 1L);
        message.read(new UserId(2L));

        message.read(new UserId(2L));

        assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(message.getReadTime()).isNotNull();
    }

    @Test
    void groupInboxMessageKeepsReadWhenReceiveAfterRead() {
        ImGroupInboxMessage message = groupMessage(900L, 101L, 1001L, 2L, 1L);
        message.read(new UserId(2L));

        message.receive(new UserId(2L));

        assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(message.getReceivedTime()).isNotNull();
    }

    @Test
    void groupInboxMessageCanOnlyBeRevokedBySender() {
        ImGroupInboxMessage message = groupMessage(900L, 101L, 1001L, 2L, 1L);

        assertThatThrownBy(() -> message.revoke(new UserId(2L)))
                .isInstanceOf(IllegalArgumentException.class);

        message.revoke(new UserId(1L));

        assertThat(message.getStatus()).isEqualTo(ImGroupMessageStatus.REVOKED);
        assertThat(message.getRevokeTime()).isNotNull();
    }

    private ImGroupInboxMessage groupMessage(Long messageId, Long chatId, Long groupId, Long userId, Long senderId) {
        return ImGroupInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken("token-" + messageId + "-" + userId))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .groupId(new GroupId(groupId))
                .chatId(new ImChatId(chatId))
                .userId(new UserId(userId))
                .senderId(new UserId(senderId))
                .status(ImGroupMessageStatus.SENT)
                .sendTime(LocalDateTime.now())
                .build();
    }
}
