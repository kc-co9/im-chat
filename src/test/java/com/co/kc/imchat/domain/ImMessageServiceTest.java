package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.GroupId;
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
    void buildGroupInboxMessageToRecipientsBuildsInboxMessagesAndUpdatesChats() {
        ImMessageService service = new ImMessageService(new FixedSnowflakeId(1L));
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
        assertThat(senderMessage.getReceivedTime()).isNotNull();
        ImGroupInboxMessage receiverMessage = transmission.getInboxMessages().stream()
                .filter(message -> message.getUserId().equals(new UserId(2L)))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.SENT);
        assertThat(receiverMessage.getReceivedTime()).isNull();
        assertThat(senderChat.getUnreadMessageCount()).isZero();
        assertThat(senderChat.getReadMessageId().getValue()).isEqualTo(900L);
        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(receiverChat.getReadMessageId()).isNull();
        assertThat(transmission.getGroupChats()).containsExactly(senderChat, receiverChat);
    }

    @Test
    void transmitGroupCreatedSystemMessageBuildsSystemMessageForEachGroupChat() {
        ImMessageService service = new ImMessageService(new FixedSnowflakeId(900L));
        UserId ownerId = new UserId(1L);
        GroupId groupId = new GroupId(1001L);
        ImGroupChat ownerChat = groupChat(101L, 1001L, 1L);
        ImGroupChat memberChat = groupChat(102L, 1001L, 2L);

        ImGroupMessageTransmission transmission =
                service.transmitGroupCreated(groupId, ownerId, ownerChat, Arrays.asList(ownerChat, memberChat));

        assertThat(transmission.getInboxMessages()).hasSize(2);
        assertThat(transmission.getInboxMessages())
                .allSatisfy(message -> {
                    assertThat(message.getId().getValue()).isEqualTo(900L);
                    assertThat(message.getToken().getValue()).isEqualTo("system:group_created:1001");
                    assertThat(message.getSenderId()).isEqualTo(ownerId);
                    assertThat(message.getContent().getType()).isEqualTo(ImMessageType.SYSTEM);
                    assertThat(message.getContent().getValue()).isEqualTo("群聊已创建");
                });
        assertThat(transmission.getSenderMessage(ownerId).getStatus()).isEqualTo(ImGroupMessageStatus.READ);
        ImGroupInboxMessage memberMessage = transmission.getInboxMessages().stream()
                .filter(message -> message.getUserId().equals(new UserId(2L)))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(memberMessage.getStatus()).isEqualTo(ImGroupMessageStatus.SENT);
        assertThat(ownerChat.getUnreadMessageCount()).isZero();
        assertThat(memberChat.getUnreadMessageCount()).isEqualTo(1);
    }

    @Test
    void buildGroupInboxMessageKeepsReceiverSentEvenWhenReceiverIsChatting() {
        ImMessageService service = new ImMessageService(new FixedSnowflakeId(1L));
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

        ImGroupInboxMessage receiverMessage = transmission.getInboxMessages().stream()
                .filter(message -> message.getUserId().equals(new UserId(2L)))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertThat(receiverMessage.getStatus()).isEqualTo(ImGroupMessageStatus.SENT);
        assertThat(receiverMessage.getReceivedTime()).isNull();
        assertThat(receiverMessage.getReadTime()).isNull();
        assertThat(receiverChat.getUnreadMessageCount()).isEqualTo(1);
        assertThat(receiverChat.getReadMessageId()).isNull();
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

    private static class FixedSnowflakeId extends com.co.kc.imchat.support.identity.snowflake.SnowflakeId {
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

    private static class TestMachineId implements com.co.kc.imchat.support.identity.snowflake.ISnowflakeMachineId {
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
