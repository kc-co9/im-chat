package com.co.kc.imchat.common.domain;

import com.co.kc.imchat.service.message.domain.chat.model.GroupChatJoin;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMember;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMembership;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatName;
import com.co.kc.imchat.service.message.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.common.domain.group.model.GroupAlias;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.common.domain.group.model.MemberDisplayName;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageRevocation;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageTransmission;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageRecipient;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageSender;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImOutboundMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageRevocation;
import com.co.kc.imchat.service.message.domain.sticker.model.ImStickerId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainValueObjectTest {

    @Test
    void simpleValueObjectsAreRecords() {
        assertThat(GroupChatJoin.class.isRecord()).isTrue();
        assertThat(GroupChatMember.class.isRecord()).isTrue();
        assertThat(GroupChatMembership.class.isRecord()).isTrue();
        assertThat(ImChatId.class.isRecord()).isTrue();
        assertThat(ImChatName.class.isRecord()).isTrue();
        assertThat(ImUserChatDescriptor.class.isRecord()).isTrue();
        assertThat(GroupAlias.class.isRecord()).isTrue();
        assertThat(GroupId.class.isRecord()).isTrue();
        assertThat(GroupUserAlias.class.isRecord()).isTrue();
        assertThat(MemberDescriptor.class.isRecord()).isTrue();
        assertThat(MemberDisplayName.class.isRecord()).isTrue();
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
        assertThat(UserId.class.isRecord()).isTrue();
        assertThat(UserName.class.isRecord()).isTrue();
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
        assertThatThrownBy(() -> new ImMessageContent(null, "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("消息类型不能为空");
    }
}
