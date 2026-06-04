package com.co.kc.imchat.infrastructure.domain;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.model.FriendEdge;
import com.co.kc.imchat.domain.friend.model.FriendId;
import com.co.kc.imchat.domain.friend.model.FriendStatus;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.infrastructure.domain.repository.CachedFriendRepository;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlFriendRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedFriendRepositoryTest {

    private final MysqlFriendRepository delegate = mock(MysqlFriendRepository.class);
    private final Cache<String, Friend> friendEdgeCache = mock(Cache.class);
    private final CachedFriendRepository repository = new CachedFriendRepository(delegate, friendEdgeCache);

    @Test
    void findByUserCachesNonEmptyFriendsOnly() {
        UserId userId = new UserId(1L);
        List<Friend> friends = List.of(friend(userId, new UserId(2L)));
        when(delegate.find(userId)).thenReturn(friends);

        List<Friend> result = repository.find(userId);

        assertThat(result).isEqualTo(friends);
        verify(delegate).find(userId);
    }

    @Test
    void findEdgeReturnsCachedFriendWithoutDelegating() {
        UserId userId = new UserId(1L);
        UserId friendUserId = new UserId(2L);
        FriendEdge edge = new FriendEdge(userId, friendUserId);
        Friend friend = friend(userId, friendUserId);
        when(friendEdgeCache.get("1:2")).thenReturn(friend);

        Optional<Friend> result = repository.find(edge);

        assertThat(result).contains(friend);
        verify(delegate, never()).find(edge);
    }

    @Test
    void findEdgeBackfillsCacheWhenFound() {
        UserId userId = new UserId(1L);
        UserId friendUserId = new UserId(2L);
        FriendEdge edge = new FriendEdge(userId, friendUserId);
        Friend friend = friend(userId, friendUserId);
        when(delegate.find(edge)).thenReturn(Optional.of(friend));

        assertThat(repository.find(edge)).contains(friend);

        verify(delegate).find(edge);
        verify(friendEdgeCache).put("1:2", friend);
    }

    @Test
    void isFriendshipActiveDelegatesExistenceCheck() {
        UserId userId = new UserId(1L);
        UserId friendUserId = new UserId(2L);
        when(delegate.isFriendshipActive(userId, friendUserId)).thenReturn(true);

        assertThat(repository.isFriendshipActive(userId, friendUserId)).isTrue();

        verify(delegate).isFriendshipActive(userId, friendUserId);
    }

    @Test
    void saveDelegatesToMysqlRepository() {
        Friend friend = friend(new UserId(1L), new UserId(2L));

        repository.save(friend);

        verify(delegate).save(friend);
    }

    private Friend friend(UserId userId, UserId friendUserId) {
        return new Friend(new FriendId(userId, friendUserId), userId, friendUserId,
                new UserName("friend"), null, FriendStatus.NORMAL, LocalDateTime.now());
    }
}
