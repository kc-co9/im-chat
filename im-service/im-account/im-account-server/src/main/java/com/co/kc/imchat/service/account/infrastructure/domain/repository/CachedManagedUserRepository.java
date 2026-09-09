package com.co.kc.imchat.service.account.infrastructure.domain.repository;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserQueryCondition;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;

import java.util.Optional;

/**
 * 管理用户仓储的缓存一致性包装，写入后清理登录侧用户缓存。
 */
public class CachedManagedUserRepository implements ManagedUserRepository {
    private final MysqlManagedUserRepository delegate;
    private final Cache<Long, User> userIdCache;
    private final Cache<String, User> userEmailCache;
    private final Cache<String, Boolean> userEmailContainCache;

    public CachedManagedUserRepository(
            MysqlManagedUserRepository delegate,
            Cache<Long, User> userIdCache,
            Cache<String, User> userEmailCache,
            Cache<String, Boolean> userEmailContainCache
    ) {
        this.delegate = delegate;
        this.userIdCache = userIdCache;
        this.userEmailCache = userEmailCache;
        this.userEmailContainCache = userEmailContainCache;
    }

    @Override
    public Optional<ManagedUser> find(UserId userId) {
        return delegate.find(userId);
    }

    @Override
    public PagingResult<ManagedUser> page(Paging paging, UserQueryCondition condition) {
        return delegate.page(paging, condition);
    }

    @Override
    public void save(ManagedUser user) {
        UserEmail previousEmail = delegate.find(user.getUserId())
                .map(ManagedUser::getEmail)
                .orElse(user.getEmail());
        delegate.save(user);
        invalidate(user, previousEmail);
    }

    @Override
    public void remove(ManagedUser user) {
        delegate.remove(user);
        invalidate(user, user.getEmail());
    }

    @Override
    public boolean contain(UserEmail email) {
        return delegate.contain(email);
    }

    private void invalidate(ManagedUser managedUser, UserEmail previousEmail) {
        userIdCache.remove(managedUser.getUserId().value());
        removeEmailCache(previousEmail);
        if (!previousEmail.equals(managedUser.getEmail())) {
            removeEmailCache(managedUser.getEmail());
        }
    }

    private void removeEmailCache(UserEmail email) {
        userEmailCache.remove(email.value());
        userEmailContainCache.remove(email.value());
    }
}
