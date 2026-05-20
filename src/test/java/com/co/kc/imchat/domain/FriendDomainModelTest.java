package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendAlias;
import com.co.kc.imchat.domain.friend.FriendDisplayName;
import com.co.kc.imchat.domain.friend.FriendEdge;
import com.co.kc.imchat.domain.friend.FriendId;
import com.co.kc.imchat.domain.friend.FriendStatus;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserName;
import com.co.kc.imchat.support.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendDomainModelTest {

    @Test
    void changeAliasUpdatesDisplayName() {
        Friend friend = normalFriend();

        friend.changeAlias(new FriendAlias("new-alias"));

        FriendDisplayName displayName = friend.displayName();
        assertThat(displayName.getValue()).isEqualTo("new-alias");
    }

    @Test
    void blockedFriendIsNotNormal() {
        Friend friend = normalFriend();

        friend.block();

        assertThat(friend.isBlocked()).isTrue();
        assertThat(friend.isNormal()).isFalse();
    }

    @Test
    void friendOperationsRejectSelfOperation() {
        assertThatThrownBy(() -> friendEdge(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能操作自己");
    }

    private FriendEdge friendEdge(Long userId, Long friendUserId) {
        return new FriendEdge(new UserId(userId), new UserId(friendUserId));
    }

    private Friend normalFriend() {
        return new Friend(new FriendId(new UserId(1L), new UserId(2L)),
                new UserId(1L),
                new UserId(2L),
                new UserName("bob"),
                null,
                FriendStatus.NORMAL,
                null);
    }

}
