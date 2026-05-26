package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupAlias;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.model.GroupName;
import com.co.kc.imchat.domain.group.model.GroupNotification;
import com.co.kc.imchat.domain.group.model.GroupMembership;
import com.co.kc.imchat.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.domain.group.model.MemberCount;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.domain.message.model.ImMessageContent;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupDomainModelTest {

    @Test
    void groupChangesMemberCount() {
        Group group = Group.builder()
                .id(new GroupId(1001L))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(1L))
                .name(new GroupName("group"))
                .memberCount(new MemberCount(1))
                .build();

        group.changeMemberCount(new MemberCount(3));

        assertThat(group.getMemberCount().value()).isEqualTo(3);
    }

    @Test
    void groupRejectsNullMemberCountChange() {
        Group group = Group.builder()
                .id(new GroupId(1001L))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(1L))
                .name(new GroupName("group"))
                .memberCount(new MemberCount(1))
                .build();

        assertThatThrownBy(() -> group.changeMemberCount(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("群人数不能为空");
    }

    @Test
    void ownerCanTransferOwnership() {
        Group group = group(1001L, 1L, 2);

        group.transferOwner(new UserId(1L), new UserId(2L));

        assertThat(group.getOwnerId().value()).isEqualTo(2L);
    }

    @Test
    void nonOwnerCannotTransferOwnership() {
        Group group = group(1001L, 1L, 2);

        assertThatThrownBy(() -> group.transferOwner(new UserId(2L), new UserId(3L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有群主");
    }

    @Test
    void ownerCannotMemberLeaveBeforeTransfer() {
        Group group = group(1001L, 1L, 2);

        assertThatThrownBy(() -> group.ensureCanLeave(new UserId(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群主不能直接退群");
    }

    @Test
    void normalMemberCanMemberLeave() {
        Group group = group(1001L, 1L, 2);

        group.ensureCanLeave(new UserId(2L));
    }

    @Test
    void ownerCannotKickSelf() {
        Group group = group(1001L, 1L, 2);

        assertThatThrownBy(() -> group.ensureCanKick(new UserId(1L), new UserId(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能踢出群主");
    }

    @Test
    void ownerCanKickNormalMember() {
        Group group = group(1001L, 1L, 2);

        group.ensureCanKick(new UserId(1L), new UserId(2L));
    }

    @Test
    void ownerCanChangeGroupNotification() {
        Group group = group(1001L, 1L, 2);

        group.changeNotification(new UserId(1L), new GroupNotification("notice"));

        assertThat(group.getNotification().value()).isEqualTo("notice");
    }

    @Test
    void nonOwnerCannotChangeGroupNotification() {
        Group group = group(1001L, 1L, 2);

        assertThatThrownBy(() -> group.changeNotification(new UserId(2L), new GroupNotification("notice")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有群主");
    }

    @Test
    void groupMemberCanChangeUserAlias() {
        GroupMember member = groupMember(1001L, 2L);

        member.changeUserAlias(new GroupUserAlias("in-group"));

        assertThat(member.getUserAlias().value()).isEqualTo("in-group");
    }

    @Test
    void groupChatCanChangeGroupAlias() {
        ImGroupChat chat = groupChat(101L, 1001L, 2L);

        chat.changeGroupAlias(new GroupAlias("work"));

        assertThat(chat.getGroupAlias().value()).isEqualTo("work");
    }

    @Test
    void memberCountRejectsNegativeValue() {
        assertThatThrownBy(() -> new MemberCount(-1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("群人数不能小于0");
    }

    @Test
    void memberCountRejectsTooLargeValue() {
        assertThatThrownBy(() -> new MemberCount(GroupMembership.MAX_MEMBER_COUNT + 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("群人数不能超过 500 人");
    }

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

        assertThat(chat.getLastMessageId().value()).isEqualTo(900L);
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

        assertThat(chat.getLastMessageId().value()).isEqualTo(900L);
        assertThat(chat.getReadMessageId().value()).isEqualTo(900L);
        assertThat(chat.getUnreadMessageCount()).isZero();
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

        assertThat(chat.getReadMessageId().value()).isEqualTo(900L);
        assertThat(chat.getUnreadMessageCount()).isZero();
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

    private GroupMember groupMember(Long groupId, Long userId) {
        GroupId imGroupId = new GroupId(groupId);
        UserId imUserId = new UserId(userId);
        return GroupMember.builder()
                .id(new MemberId(imGroupId, imUserId))
                .groupId(imGroupId)
                .userId(imUserId)
                .joinTime(LocalDateTime.now())
                .build();
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

    private Group group(Long groupId, Long ownerId, int memberCount) {
        return Group.builder()
                .id(new GroupId(groupId))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(ownerId))
                .name(new GroupName("group"))
                .memberCount(new MemberCount(memberCount))
                .build();
    }
}
