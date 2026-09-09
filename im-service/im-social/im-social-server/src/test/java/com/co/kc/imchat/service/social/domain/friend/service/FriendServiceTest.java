package com.co.kc.imchat.service.social.domain.friend.service;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.model.FriendAlias;
import com.co.kc.imchat.service.social.domain.friend.model.FriendId;
import com.co.kc.imchat.service.social.domain.friend.model.FriendProfile;
import com.co.kc.imchat.service.social.domain.friend.model.FriendStatus;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FriendServiceTest {

    @Test
    void describesFriendsUsingAliasUsernameAndUserIdInPriorityOrder() {
        FriendService service = new FriendService(mock(FriendRepository.class));
        Friend aliasedFriend = friend(1001L, 2001L, "备注名");
        Friend namedFriend = friend(1001L, 2002L, null);
        Friend missingAccountFriend = friend(1001L, 2003L, null);
        Map<UserId, UserProfile> userProfiles = Map.of(
                new UserId(2001L), new UserProfile(new UserId(2001L), "first-name", "first@example.com"),
                new UserId(2002L), new UserProfile(new UserId(2002L), "second-name", "second@example.com"));

        List<FriendProfile> result = service.describeFriends(
                List.of(aliasedFriend, namedFriend, missingAccountFriend),
                userProfiles);

        assertThat(result)
                .extracting(profile -> profile.friendUserId().value() + ":" + profile.displayName().value())
                .containsExactly("2001:备注名", "2002:second-name", "2003:2003");
        assertThat(result).extracting(FriendProfile::status)
                .containsExactly(FriendStatus.NORMAL, FriendStatus.NORMAL, FriendStatus.NORMAL);
    }

    private Friend friend(Long userId, Long friendUserId, String alias) {
        UserId owner = new UserId(userId);
        UserId target = new UserId(friendUserId);
        return new Friend(
                new FriendId(owner, target),
                owner,
                target,
                alias == null ? null : new FriendAlias(alias),
                FriendStatus.NORMAL,
                Instant.parse("2026-09-09T00:00:00Z"));
    }
}
