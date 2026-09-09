package com.co.kc.imchat.service.social.application;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.service.social.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.social.adapter.message.MessageSocialAdapter;
import com.co.kc.imchat.service.social.domain.account.model.UserProfile;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.model.FriendAlias;
import com.co.kc.imchat.service.social.domain.friend.model.FriendId;
import com.co.kc.imchat.service.social.domain.friend.model.FriendStatus;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.service.social.domain.friend.service.FriendService;
import com.co.kc.imchat.service.social.facade.dto.FriendDisplayDTO;
import com.co.kc.imchat.service.social.facade.params.FriendDisplaysGetParams;
import com.co.kc.imchat.service.social.model.cqrs.dto.friend.FriendItemDTO;
import com.co.kc.imchat.service.social.model.cqrs.query.friend.FriendListQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendAppServiceTest {

    @Test
    void friendListUsesAccountUsernameWhenAliasIsAbsent() {
        AccountAdapter accountAdapter = mock(AccountAdapter.class);
        FriendRepository friendRepository = mock(FriendRepository.class);
        Friend friend = friend(1001L, 2001L, null);
        when(friendRepository.find(new UserId(1001L))).thenReturn(List.of(friend));
        when(accountAdapter.findUserProfiles(List.of(new UserId(2001L))))
                .thenReturn(Map.of(new UserId(2001L), new UserProfile(
                        new UserId(2001L), "friend-name", "friend@example.com")));
        FriendAppService service = service(accountAdapter, friendRepository);

        List<FriendItemDTO> result = service.getFriendList(new FriendListQuery(1001L));

        assertThat(result).extracting(FriendItemDTO::getDisplayName)
                .containsExactly("friend-name");
        verify(accountAdapter).findUserProfiles(List.of(new UserId(2001L)));
    }

    @Test
    void friendDisplaysPreferAliasAndOtherwiseUseAccountUsername() {
        AccountAdapter accountAdapter = mock(AccountAdapter.class);
        FriendRepository friendRepository = mock(FriendRepository.class);
        UserId userId = new UserId(1001L);
        List<UserId> friendUserIds = List.of(new UserId(2001L), new UserId(2002L));
        List<Friend> friends = List.of(
                friend(1001L, 2001L, "备注名"),
                friend(1001L, 2002L, null));
        when(friendRepository.find(userId, friendUserIds)).thenReturn(friends);
        when(accountAdapter.findUserProfiles(friendUserIds)).thenReturn(Map.of(
                new UserId(2001L), new UserProfile(
                        new UserId(2001L), "first-name", "first@example.com"),
                new UserId(2002L), new UserProfile(
                        new UserId(2002L), "second-name", "second@example.com")));
        FriendAppService service = service(accountAdapter, friendRepository);

        List<FriendDisplayDTO> result = service.getFriendDisplays(
                new FriendDisplaysGetParams(1001L, List.of(2001L, 2002L)));

        assertThat(result)
                .extracting(friend -> friend.userId() + ":" + friend.displayName())
                .containsExactly("2001:备注名", "2002:second-name");
        verify(accountAdapter).findUserProfiles(friendUserIds);
    }

    private FriendAppService service(AccountAdapter accountAdapter, FriendRepository friendRepository) {
        return new FriendAppService(
                accountAdapter,
                friendRepository,
                new FriendService(friendRepository),
                mock(MessageSocialAdapter.class),
                mock(AfterTransactionCommitTemplate.class));
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
