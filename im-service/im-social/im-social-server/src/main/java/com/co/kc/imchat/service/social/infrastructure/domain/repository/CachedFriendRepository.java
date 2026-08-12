package com.co.kc.imchat.service.social.infrastructure.domain.repository;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.model.FriendEdge;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.social.infrastructure.support.constant.SocialCacheNames;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CachedFriendRepository implements FriendRepository {
    private final MysqlFriendRepository delegate;
    private final Cache<String, Friend> friendEdgeCache;

    public CachedFriendRepository(MysqlFriendRepository delegate, Cache<String, Friend> friendEdgeCache) {
        this.delegate = delegate;
        this.friendEdgeCache = friendEdgeCache;
    }

    @Override
    @Cached(name = SocialCacheNames.FRIENDS_USER, key = "#userId.value()", cacheType = CacheType.REMOTE,
            expire = 5, timeUnit = TimeUnit.MINUTES, postCondition = "!#result.isEmpty()")
    public List<Friend> find(UserId userId) {
        return delegate.find(userId);
    }

    @Override
    public List<Friend> find(UserId userId, List<UserId> friendUserIds) {
        return delegate.find(userId, friendUserIds);
    }

    @Override
    public Optional<Friend> find(FriendEdge edge) {
        String key = relationKey(edge.userId(), edge.friendUserId());
        Friend cachedFriend = friendEdgeCache.get(key);
        if (cachedFriend != null) {
            return Optional.of(cachedFriend);
        }
        Optional<Friend> friend = delegate.find(edge);
        friend.ifPresent(it -> friendEdgeCache.put(key, it));
        return friend;
    }

    @Override
    public boolean contain(UserId userId, UserId friendUserId) {
        return delegate.contain(userId, friendUserId);
    }

    @Override
    @Cached(name = SocialCacheNames.FRIEND_ACTIVE, key = "#userId.value() + ':' + #friendUserId.value()",
            cacheType = CacheType.REMOTE, expire = 5, timeUnit = TimeUnit.MINUTES)
    public boolean isFriendshipActive(UserId userId, UserId friendUserId) {
        return delegate.isFriendshipActive(userId, friendUserId);
    }

    @Override
    @CacheInvalidate(name = SocialCacheNames.FRIENDS_USER, key = "#friend.getUserId().value()")
    @CacheInvalidate(name = SocialCacheNames.FRIEND_EDGE, key = "#friend.getUserId().value() + ':' + #friend.getFriendUserId().value()")
    @CacheInvalidate(name = SocialCacheNames.FRIEND_ACTIVE, key = "#friend.getUserId().value() + ':' + #friend.getFriendUserId().value()")
    public void save(Friend friend) {
        delegate.save(friend);
    }

    @Override
    @CacheInvalidate(name = SocialCacheNames.FRIENDS_USER, key = "#friend.getUserId().value()")
    @CacheInvalidate(name = SocialCacheNames.FRIEND_EDGE, key = "#friend.getUserId().value() + ':' + #friend.getFriendUserId().value()")
    @CacheInvalidate(name = SocialCacheNames.FRIEND_ACTIVE, key = "#friend.getUserId().value() + ':' + #friend.getFriendUserId().value()")
    public void remove(Friend friend) {
        delegate.remove(friend);
    }

    private String relationKey(UserId userId, UserId friendUserId) {
        return userId.value() + ":" + friendUserId.value();
    }

}
