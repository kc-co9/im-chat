package com.co.kc.imchat.service.account.infrastructure.domain.repository;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.infrastructure.support.constant.AccountCacheNames;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CachedUserRepository implements UserRepository {
    private final MysqlUserRepository delegate;
    private final Cache<Long, User> userIdCache;
    private final Cache<String, User> userEmailCache;
    private final Cache<String, Boolean> userEmailContainCache;

    public CachedUserRepository(MysqlUserRepository delegate, Cache<Long, User> userIdCache,
                                Cache<String, User> userEmailCache, Cache<String, Boolean> userEmailContainCache) {
        this.delegate = delegate;
        this.userIdCache = userIdCache;
        this.userEmailCache = userEmailCache;
        this.userEmailContainCache = userEmailContainCache;
    }

    @Override
    public Optional<User> find(UserId userId) {
        User cachedUser = userIdCache.get(userId.value());
        if (cachedUser != null) {
            return Optional.of(cachedUser);
        }
        Optional<User> user = delegate.find(userId);
        user.ifPresent(it -> userIdCache.put(userId.value(), it));
        return user;
    }

    @Override
    public List<User> find(List<UserId> userIds) {
        return delegate.find(userIds);
    }

    @Override
    public Optional<User> find(UserEmail email) {
        User cachedUser = userEmailCache.get(email.value());
        if (cachedUser != null) {
            return Optional.of(cachedUser);
        }
        Optional<User> user = delegate.find(email);
        user.ifPresent(it -> userEmailCache.put(email.value(), it));
        return user;
    }

    @Override
    @CacheInvalidate(name = AccountCacheNames.USER_ID, key = "#user.getId().value()")
    @CacheInvalidate(name = AccountCacheNames.USER_EMAIL, key = "#user.getEmail().value()")
    @CacheInvalidate(name = AccountCacheNames.USER_EMAIL_CONTAIN, key = "#user.getEmail().value()")
    public void save(User user) {
        removePreviousEmailCache(user);
        delegate.save(user);
    }

    @Override
    @CacheInvalidate(name = AccountCacheNames.USER_ID, key = "#user.getId().value()")
    @CacheInvalidate(name = AccountCacheNames.USER_EMAIL, key = "#user.getEmail().value()")
    @CacheInvalidate(name = AccountCacheNames.USER_EMAIL_CONTAIN, key = "#user.getEmail().value()")
    public void remove(User user) {
        delegate.remove(user);
    }

    @Override
    @Cached(name = AccountCacheNames.USER_EMAIL_CONTAIN, key = "#email.value()", cacheType = CacheType.REMOTE,
            expire = 10, timeUnit = TimeUnit.MINUTES)
    public boolean contain(UserEmail email) {
        return delegate.contain(email);
    }

    private void removePreviousEmailCache(User user) {
        delegate.find(user.getId())
                .filter(previous -> !previous.getEmail().equals(user.getEmail()))
                .ifPresent(previous -> {
                    String previousEmail = previous.getEmail().value();
                    userEmailCache.remove(previousEmail);
                    userEmailContainCache.remove(previousEmail);
                });
    }
}
