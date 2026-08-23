package com.co.kc.imchat.service.message.domain.chat.service;

import com.co.kc.imchat.common.domain.group.model.GroupAlias;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.service.message.domain.social.model.FriendDisplay;
import com.co.kc.imchat.service.message.domain.social.model.GroupSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImChatServiceTest {

    private final ImChatService service = new ImChatService(null, null, null);

    @Test
    void describePrivateChatsUsesFriendDisplayNameAndLastMessage() {
        ImPrivateChat chat = privateChat(101L, 1L, 2L, LocalDateTime.of(2026, 6, 25, 10, 0));
        chat.setLastMessageId(new ImMessageId(900L));
        ImMessage lastMessage = privateMessage(900L, 101L, 1L, 2L);

        List<ImUserChatDescriptor> descriptors = service.describePrivateChats(
                List.of(chat),
                Map.of(2L, new FriendDisplay(2L, "alice")),
                Map.of(900L, lastMessage));

        assertThat(descriptors).hasSize(1);
        ImUserChatDescriptor descriptor = descriptors.getFirst();
        assertThat(descriptor.chatName().value()).isEqualTo("alice");
        assertThat(descriptor.chatLastMessage()).isSameAs(lastMessage);
    }

    @Test
    void describeGroupChatsSkipsInactiveGroupAndUsesAliasBeforeGroupName() {
        ImGroupChat aliasedChat = groupChat(101L, 1001L, 1L, LocalDateTime.of(2026, 6, 25, 10, 0));
        aliasedChat.setGroupAlias(new GroupAlias("work"));
        ImGroupChat inactiveChat = groupChat(102L, 1002L, 1L, LocalDateTime.of(2026, 6, 25, 11, 0));

        List<ImUserChatDescriptor> descriptors = service.describeGroupChats(
                List.of(aliasedChat, inactiveChat),
                Map.of(
                        1001L, new GroupSummary(1001L, "default", true),
                        1002L, new GroupSummary(1002L, "inactive", false)),
                Map.of());

        assertThat(descriptors).hasSize(1);
        assertThat(descriptors.getFirst().chatName().value()).isEqualTo("work");
        assertThat(descriptors.getFirst().chatId()).isEqualTo(new ImChatId(101L));
    }

    @Test
    void mergeChatDescriptorsSortsByActiveTimeDescendingWithNullLast() {
        ImUserChatDescriptor oldChat = descriptor(101L, LocalDateTime.of(2026, 6, 25, 10, 0));
        ImUserChatDescriptor newChat = descriptor(102L, LocalDateTime.of(2026, 6, 25, 11, 0));
        ImUserChatDescriptor inactiveChat = descriptor(103L, null);

        List<ImUserChatDescriptor> descriptors = service.mergeChatDescriptors(
                List.of(oldChat, inactiveChat),
                List.of(newChat));

        assertThat(descriptors).extracting(descriptor -> descriptor.chatId().value())
                .containsExactly(102L, 101L, 103L);
    }

    @Test
    void mergeChatDescriptorsAllowsNullInputs() {
        ImUserChatDescriptor chat = descriptor(101L, LocalDateTime.of(2026, 6, 25, 10, 0));

        assertThat(service.mergeChatDescriptors(null, List.of(chat))).containsExactly(chat);
        assertThat(service.mergeChatDescriptors(null, null)).isEmpty();
    }

    @Test
    void visibleChatsFilterHiddenChats() {
        ImPrivateChat visiblePrivateChat = privateChat(101L, 1L, 2L, LocalDateTime.now());
        ImPrivateChat hiddenPrivateChat = privateChat(102L, 1L, 3L, LocalDateTime.now());
        hiddenPrivateChat.hide();
        ImGroupChat visibleGroupChat = groupChat(201L, 1001L, 1L, LocalDateTime.now());
        ImGroupChat hiddenGroupChat = groupChat(202L, 1002L, 1L, LocalDateTime.now());
        hiddenGroupChat.hide();

        assertThat(service.visiblePrivateChats(List.of(visiblePrivateChat, hiddenPrivateChat)))
                .containsExactly(visiblePrivateChat);
        assertThat(service.visibleGroupChats(List.of(visibleGroupChat, hiddenGroupChat)))
                .containsExactly(visibleGroupChat);
    }

    private ImUserChatDescriptor descriptor(Long chatId, LocalDateTime activeTime) {
        return new ImUserChatDescriptor(new ImChatId(chatId), null, ImChatType.PRIVATE, null, activeTime);
    }

    private ImPrivateChat privateChat(Long chatId, Long userId, Long peerUserId, LocalDateTime activeTime) {
        return ImPrivateChat.builder()
                .id(new ImChatId(chatId))
                .type(ImChatType.PRIVATE)
                .userId(new UserId(userId))
                .peerUserId(new UserId(peerUserId))
                .activeTime(activeTime)
                .build();
    }

    private ImGroupChat groupChat(Long chatId, Long groupId, Long userId, LocalDateTime activeTime) {
        return ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .type(ImChatType.GROUP)
                .groupId(new GroupId(groupId))
                .userId(new UserId(userId))
                .status(ImChatStatus.NORMAL)
                .activeTime(activeTime)
                .unreadMessageCount(0)
                .build();
    }

    private ImPrivateInboxMessage privateMessage(Long messageId, Long chatId, Long userId, Long senderId) {
        return ImPrivateInboxMessage.builder()
                .id(new ImMessageId(messageId))
                .token(new ImMessageToken("token-" + messageId))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .chatId(new ImChatId(chatId))
                .userId(new UserId(userId))
                .senderId(new UserId(senderId))
                .status(ImPrivateMessageStatus.SENT)
                .sendTime(LocalDateTime.now())
                .build();
    }
}
