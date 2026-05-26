package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.chat.model.GroupChatJoin;
import com.co.kc.imchat.domain.chat.model.GroupChatMembership;
import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.model.ImChatName;
import com.co.kc.imchat.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.domain.friend.model.FriendAlias;
import com.co.kc.imchat.domain.friend.model.FriendDisplayName;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.friend.model.FriendId;
import com.co.kc.imchat.domain.group.model.GroupAlias;
import com.co.kc.imchat.domain.group.model.GroupCreation;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMemberDeparture;
import com.co.kc.imchat.domain.group.model.GroupMemberInvitation;
import com.co.kc.imchat.domain.group.model.GroupMembership;
import com.co.kc.imchat.domain.group.model.GroupName;
import com.co.kc.imchat.domain.group.model.GroupNotification;
import com.co.kc.imchat.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.domain.group.model.MemberCount;
import com.co.kc.imchat.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.domain.group.model.MemberDisplayName;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.group.model.UserGroupDescriptor;
import com.co.kc.imchat.domain.message.model.ImGroupMessageRevocation;
import com.co.kc.imchat.domain.message.model.ImGroupMessageTransmission;
import com.co.kc.imchat.domain.message.model.ImMessageContent;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.model.ImMessageRecipient;
import com.co.kc.imchat.domain.message.model.ImMessageSender;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImOutboundMessage;
import com.co.kc.imchat.domain.message.model.ImPrivateMessageRevocation;
import com.co.kc.imchat.domain.sticker.model.ImStickerId;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.domain.user.model.UserPassword;
import com.co.kc.imchat.domain.user.model.UserRawPassword;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainValueObjectTest {

    @Test
    void simpleValueObjectsAreRecords() {
        assertThat(GroupChatJoin.class.isRecord()).isTrue();
        assertThat(GroupChatMembership.class.isRecord()).isTrue();
        assertThat(ImChatId.class.isRecord()).isTrue();
        assertThat(ImChatName.class.isRecord()).isTrue();
        assertThat(ImUserChatDescriptor.class.isRecord()).isTrue();
        assertThat(FriendAlias.class.isRecord()).isTrue();
        assertThat(FriendDisplayName.class.isRecord()).isTrue();
        assertThat(FriendEdge.class.isRecord()).isTrue();
        assertThat(FriendId.class.isRecord()).isTrue();
        assertThat(GroupAlias.class.isRecord()).isTrue();
        assertThat(GroupCreation.class.isRecord()).isTrue();
        assertThat(GroupId.class.isRecord()).isTrue();
        assertThat(GroupMemberDeparture.class.isRecord()).isTrue();
        assertThat(GroupMemberInvitation.class.isRecord()).isTrue();
        assertThat(GroupMembership.class.isRecord()).isTrue();
        assertThat(GroupName.class.isRecord()).isTrue();
        assertThat(GroupNotification.class.isRecord()).isTrue();
        assertThat(GroupUserAlias.class.isRecord()).isTrue();
        assertThat(MemberCount.class.isRecord()).isTrue();
        assertThat(MemberDescriptor.class.isRecord()).isTrue();
        assertThat(MemberDisplayName.class.isRecord()).isTrue();
        assertThat(MemberId.class.isRecord()).isTrue();
        assertThat(UserGroupDescriptor.class.isRecord()).isTrue();
        assertThat(ImGroupMessageRevocation.class.isRecord()).isTrue();
        assertThat(ImGroupMessageTransmission.class.isRecord()).isTrue();
        assertThat(ImMessageContent.class.isRecord()).isTrue();
        assertThat(ImMessageId.class.isRecord()).isTrue();
        assertThat(ImMessageRecipient.class.isRecord()).isTrue();
        assertThat(ImMessageSender.class.isRecord()).isTrue();
        assertThat(ImMessageToken.class.isRecord()).isTrue();
        assertThat(ImOutboundMessage.class.isRecord()).isTrue();
        assertThat(ImPrivateMessageRevocation.class.isRecord()).isTrue();
        assertThat(ImStickerId.class.isRecord()).isTrue();
        assertThat(UserEmail.class.isRecord()).isTrue();
        assertThat(UserId.class.isRecord()).isTrue();
        assertThat(UserName.class.isRecord()).isTrue();
        assertThat(UserPassword.class.isRecord()).isTrue();
        assertThat(UserRawPassword.class.isRecord()).isTrue();
    }

    @Test
    void valueObjectValidationMessagesAreChinese() {
        assertThatThrownBy(() -> new ImChatId(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("聊天ID不能为空");
        assertThatThrownBy(() -> new ImChatName(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("聊天名称不能为空");
        assertThatThrownBy(() -> new ImStickerId(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("表情ID不能为空");
        assertThatThrownBy(() -> new UserEmail("bad-email"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("邮箱格式不合法");
        assertThatThrownBy(() -> new UserRawPassword("中文"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("密码包含非法字符");
        assertThatThrownBy(() -> new MemberCount(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("群人数不能小于0");
        assertThatThrownBy(() -> new ImMessageContent(null, "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("消息类型不能为空");
    }
}
