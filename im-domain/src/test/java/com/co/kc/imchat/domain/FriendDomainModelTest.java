package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendAlias;
import com.co.kc.imchat.domain.friend.model.FriendDisplayName;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.friend.model.FriendId;
import com.co.kc.imchat.domain.friend.model.FriendStatus;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendDomainModelTest {

    @Test
    void changeAliasUpdatesDisplayName() {
        Friend friend = normalFriend();

        friend.changeAlias(new FriendAlias("new-alias"));

        FriendDisplayName displayName = friend.displayName();
        assertThat(displayName.value()).isEqualTo("new-alias");
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
