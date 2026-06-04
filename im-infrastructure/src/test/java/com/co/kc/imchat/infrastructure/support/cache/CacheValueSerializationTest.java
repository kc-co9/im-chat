package com.co.kc.imchat.infrastructure.support.cache;

import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendId;
import com.co.kc.imchat.domain.friend.model.FriendStatus;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.model.GroupName;
import com.co.kc.imchat.domain.group.model.GroupNotification;
import com.co.kc.imchat.domain.group.model.GroupStatus;
import com.co.kc.imchat.domain.group.model.MemberCount;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.domain.user.model.UserPassword;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class CacheValueSerializationTest {

    @Test
    void cachedDomainValuesSupportJavaSerialization() {
        assertThatCode(() -> serialize(user()))
                .doesNotThrowAnyException();
        assertThatCode(() -> serialize(group()))
                .doesNotThrowAnyException();
        assertThatCode(() -> serialize(groupMember()))
                .doesNotThrowAnyException();
        assertThatCode(() -> serialize(friend()))
                .doesNotThrowAnyException();
        assertThatCode(() -> serialize(List.of(groupMember())))
                .doesNotThrowAnyException();
        assertThatCode(() -> serialize(List.of(friend())))
                .doesNotThrowAnyException();
    }

    private void serialize(Object value) throws Exception {
        try (ObjectOutputStream output = new ObjectOutputStream(new ByteArrayOutputStream())) {
            output.writeObject(value);
        }
    }

    private User user() {
        return new User(new UserId(1L), new UserEmail("one@example.com"), new UserName("one"), new UserPassword("encoded"));
    }

    private Group group() {
        return Group.builder()
                .id(new GroupId(1001L))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(1L))
                .name(new GroupName("group"))
                .notification(new GroupNotification(""))
                .memberCount(new MemberCount(2))
                .status(GroupStatus.ACTIVE)
                .build();
    }

    private GroupMember groupMember() {
        GroupId groupId = new GroupId(1001L);
        UserId userId = new UserId(1L);
        return GroupMember.builder()
                .id(new MemberId(groupId, userId))
                .groupId(groupId)
                .userId(userId)
                .joinTime(LocalDateTime.now())
                .build();
    }

    private Friend friend() {
        UserId userId = new UserId(1L);
        UserId friendUserId = new UserId(2L);
        return new Friend(new FriendId(userId, friendUserId), userId, friendUserId,
                new UserName("friend"), null, FriendStatus.NORMAL, LocalDateTime.now());
    }
}
