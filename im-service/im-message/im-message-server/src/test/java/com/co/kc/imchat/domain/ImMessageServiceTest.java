package com.co.kc.imchat.common.domain;

import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMembership;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageRevocation;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageTransmission;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageRecipient;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageSender;
import com.co.kc.imchat.service.message.domain.message.model.ImOutboundMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.service.ImMessageService;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageRevocation;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.identity.snowflake.ISnowflakeMachineId;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImMessageServiceTest {

    @Test
    void buildGroupInboxMessageToRecipientsBuildsInboxMessagesAndUpdatesChats() {
        ImMessageService service = new ImMessageService(null, null, new FixedSnowflakeId(1L));
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

        assertThat(transmission.inboxMessages()).hasSize(2);
        ImGroupInboxMessage senderMessage = transmission.getSenderMessage(senderId);
        assertThat(senderMessage.getChatId().value()).isEqualTo(101L);
        assertThat(senderMessage.getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        assertThat(senderMessage.getReadTime()).isNotNull();
        assertThat(senderMessage.getReceivedTime()).isNotNull();
        ImGroupInboxMessage receiverMessage = transmission.inboxMessages().stream()
                .filter(message -> message.getUserId().equals(new UserId(2L)))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.SENT);
        assertThat(receiverMessage.getReceivedTime()).isNull();
        assertThat(senderChat.getUnreadMessageCount()).isZero();
        assertThat(senderChat.getReadMessageId().value()).isEqualTo(900L);
        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(receiverChat.getReadMessageId()).isNull();
        assertThat(transmission.groupChats()).containsExactly(senderChat, receiverChat);
    }

    @Test
    void transmitGroupCreatedSystemMessageBuildsSystemMessageForEachGroupChat() {
        ImMessageService service = new ImMessageService(null, null, new FixedSnowflakeId(900L));
        UserId ownerId = new UserId(1L);
        GroupId groupId = new GroupId(1001L);
        ImGroupChat ownerChat = groupChat(101L, 1001L, 1L);
        ImGroupChat memberChat = groupChat(102L, 1001L, 2L);

        ImGroupMessageTransmission transmission =
                service.transmitGroupCreated(ownerId, new GroupChatMembership(groupId, Arrays.asList(ownerChat, memberChat)));

        assertThat(transmission.inboxMessages()).hasSize(2);
        assertThat(transmission.inboxMessages())
                .allSatisfy(message -> {
                    assertThat(message.getId().value()).isEqualTo(900L);
                    assertThat(message.getToken().value()).isEqualTo("system:group_created:1001");
                    assertThat(message.getSenderId()).isEqualTo(ownerId);
                    assertThat(message.getContent().type()).isEqualTo(ImMessageType.SYSTEM);
                    assertThat(message.getContent().value()).isEqualTo("群聊已创建");
                });
        assertThat(transmission.getSenderMessage(ownerId).getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        ImGroupInboxMessage memberMessage = transmission.inboxMessages().stream()
                .filter(message -> message.getUserId().equals(new UserId(2L)))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(memberMessage.getStatus()).isEqualTo(ImGroupMessageStatus.SENT);
        assertThat(ownerChat.getUnreadMessageCount()).isZero();
        assertThat(memberChat.getUnreadMessageCount()).isEqualTo(1);
    }

    @Test
    void buildGroupInboxMessageKeepsReceiverSentEvenWhenReceiverIsChatting() {
        ImMessageService service = new ImMessageService(null, null, new FixedSnowflakeId(1L));
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
                        new ImMessageRecipient(receiverChat, true)));

        ImGroupInboxMessage receiverMessage = transmission.inboxMessages().stream()
                .filter(message -> message.getUserId().equals(new UserId(2L)))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.SENT);
        assertThat(receiverMessage.getReceivedTime()).isNull();
        assertThat(receiverMessage.getReadTime()).isNull();
        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(receiverChat.getReadMessageId()).isNull();
    }

    @Test
    void revokePrivateMessageRevokesBothInboxCopies() {
        ImMessageService service = new ImMessageService(null, null, new FixedSnowflakeId(1L));
        ImPrivateInboxMessage senderMessage = privateMessage(900L, 101L, 1L, 1L);
        ImPrivateInboxMessage receiverMessage = privateMessage(900L, 102L, 2L, 1L);

        ImPrivateMessageRevocation revocation =
                service.revokePrivateMessage(senderMessage, receiverMessage, new UserId(1L));

        assertThat(revocation.getMessages()).containsExactly(senderMessage, receiverMessage);
        assertThat(revocation.senderMessage()).isSameAs(senderMessage);
        assertThat(revocation.receiverMessage()).isSameAs(receiverMessage);
        assertThat(senderMessage.getStatus()).isEqualTo(ImPrivateMessageStatus.REVOKED);
        assertThat(receiverMessage.getStatus()).isEqualTo(ImPrivateMessageStatus.REVOKED);
        assertThat(senderMessage.getRevokeTime()).isNotNull();
        assertThat(receiverMessage.getRevokeTime()).isNotNull();
    }

    @Test
    void revokePrivateMessageDoesNotMutateOneCopyWhenAnotherCopyCannotBeRevoked() {
        ImMessageService service = new ImMessageService(null, null, new FixedSnowflakeId(1L));
        ImPrivateInboxMessage senderMessage = privateMessage(900L, 101L, 1L, 1L);
        ImPrivateInboxMessage receiverMessage = privateMessage(900L, 102L, 2L, 2L);

        assertThatThrownBy(() -> service.revokePrivateMessage(senderMessage, receiverMessage, new UserId(1L)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(senderMessage.getStatus()).isEqualTo(ImPrivateMessageStatus.SENT);
        assertThat(senderMessage.getRevokeTime()).isNull();
        assertThat(receiverMessage.getStatus()).isEqualTo(ImPrivateMessageStatus.SENT);
        assertThat(receiverMessage.getRevokeTime()).isNull();
    }

    @Test
    void revokeGroupMessageRevokesAllCopiesAndReturnsSenderCopy() {
        ImMessageService service = new ImMessageService(null, null, new FixedSnowflakeId(1L));
        ImGroupInboxMessage senderMessage = groupMessage(900L, 101L, 1001L, 1L, 1L);
        ImGroupInboxMessage receiverMessage = groupMessage(900L, 102L, 1001L, 2L, 1L);

        ImGroupMessageRevocation revocation =
                service.revokeGroupMessage(Arrays.asList(senderMessage, receiverMessage), senderMessage, new UserId(1L));

        assertThat(revocation.messages()).containsExactly(senderMessage, receiverMessage);
        assertThat(revocation.senderMessage()).isSameAs(senderMessage);
        assertThat(senderMessage.getStatus()).isEqualTo(ImGroupMessageStatus.REVOKED);
        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.REVOKED);
        assertThat(senderMessage.getRevokeTime()).isNotNull();
        assertThat(receiverMessage.getRevokeTime()).isNotNull();
    }

    @Test
    void revokeGroupMessageDoesNotMutateCopiesWhenSenderCopyMissing() {
        ImMessageService service = new ImMessageService(null, null, new FixedSnowflakeId(1L));
        ImGroupInboxMessage senderMessage = groupMessage(900L, 101L, 1001L, 1L, 1L);
        ImGroupInboxMessage receiverMessage = groupMessage(900L, 102L, 1001L, 2L, 1L);

        assertThatThrownBy(() ->
                service.revokeGroupMessage(Arrays.asList(receiverMessage), senderMessage, new UserId(1L)))
                .isInstanceOf(NotFoundException.class);

        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.SENT);
        assertThat(receiverMessage.getRevokeTime()).isNull();
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId) {
        return ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .groupId(new GroupId(groupId))
                .userId(new UserId(userId))
                .type(ImChatType.GROUP)
                .unreadMessageCount(0)
                .build();
    }

    private ImPrivateInboxMessage privateMessage(Long messageId, Long chatId, Long userId, Long senderId) {
        return ImPrivateInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken("token-" + messageId + "-" + userId))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .chatId(new ImChatId(chatId))
                .userId(new UserId(userId))
                .senderId(new UserId(senderId))
                .status(ImPrivateMessageStatus.SENT)
                .sendTime(LocalDateTime.now())
                .build();
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

    private static class FixedSnowflakeId extends SnowflakeId {
        private long next;

        FixedSnowflakeId(long next) {
            super(new TestMachineId());
            this.next = next;
        }

        @Override
        public synchronized Long next() {
            return next++;
        }
    }

    private static class TestMachineId implements ISnowflakeMachineId {
        @Override
        public long getDataCenterId() {
            return 1L;
        }

        @Override
        public long getMachineId() {
            return 1L;
        }
    }
}
