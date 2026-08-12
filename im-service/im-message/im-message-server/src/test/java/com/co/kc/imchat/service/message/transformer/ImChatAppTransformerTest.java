package com.co.kc.imchat.service.message.transformer;

import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMember;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatStatus;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatName;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.service.message.facade.dto.UserGroupChatSummaryDTO;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberDescriptorParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberParams;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageStatus;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

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

    @Test
    void groupChatMembersUseFallbackGroupIdWhenParamsGroupIdIsEmpty() {
        List<GroupChatMember> members = ImChatAppTransformer.INSTANCE.groupChatMembersFrom(
                new GroupId(1001L),
                List.of(
                        new GroupChatMemberParams(null, 1L, LocalDateTime.now()),
                        new GroupChatMemberParams(1002L, 2L, LocalDateTime.now())));

        assertThat(members).extracting(member -> member.groupId().value())
                .containsExactly(1001L, 1002L);
        assertThat(members).extracting(member -> member.userId().value())
                .containsExactly(1L, 2L);
    }

    @Test
    void memberDescriptorKeepsDisplayNameAndJoinTime() {
        LocalDateTime joinTime = LocalDateTime.of(2026, 6, 24, 10, 30);

        List<MemberDescriptor> descriptors = ImChatAppTransformer.INSTANCE.memberDescriptorsFrom(
                List.of(new GroupChatMemberDescriptorParams(1L, "alice", joinTime)));

        assertThat(descriptors).hasSize(1);
        MemberDescriptor descriptor = descriptors.getFirst();
        assertThat(descriptor.userId().value()).isEqualTo(1L);
        assertThat(descriptor.displayName().value()).isEqualTo("alice");
        assertThat(descriptor.joinTime()).isEqualTo(joinTime);
    }

    @Test
    void userGroupChatSummaryContainsChatIdentityAndUnreadState() {
        LocalDateTime activeTime = LocalDateTime.of(2026, 6, 24, 11, 0);
        ImGroupChat chat = ImGroupChat.builder()
                .id(new ImChatId(101L))
                .type(ImChatType.GROUP)
                .groupId(new GroupId(1001L))
                .userId(new UserId(1L))
                .status(ImChatStatus.NORMAL)
                .unreadMessageCount(3)
                .activeTime(activeTime)
                .build();

        UserGroupChatSummaryDTO summary = ImChatAppTransformer.INSTANCE.userGroupChatSummaryDtoFrom(chat);

        assertThat(summary.found()).isTrue();
        assertThat(summary.chatId()).isEqualTo(101L);
        assertThat(summary.groupId()).isEqualTo(1001L);
        assertThat(summary.userId()).isEqualTo(1L);
        assertThat(summary.unreadMessageCount()).isEqualTo(3);
        assertThat(summary.activeTime()).isEqualTo(activeTime);
    }
}
