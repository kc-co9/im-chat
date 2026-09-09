package com.co.kc.imchat.service.account.infrastructure.domain.repository;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedManagedUserRepositoryTest {

    @Test
    void saveInvalidatesIdentityAndBothEmailCacheKeys() {
        MysqlManagedUserRepository delegate = mock(MysqlManagedUserRepository.class);
        Cache<Long, User> userIdCache = mock(Cache.class);
        Cache<String, User> userEmailCache = mock(Cache.class);
        Cache<String, Boolean> emailContainCache = mock(Cache.class);
        ManagedUser previous = managedUser(new UserEmail("old@example.com"));
        ManagedUser changed = managedUser(new UserEmail("new@example.com"));
        when(delegate.find(changed.getUserId())).thenReturn(Optional.of(previous));
        CachedManagedUserRepository repository = new CachedManagedUserRepository(
                delegate, userIdCache, userEmailCache, emailContainCache);

        repository.save(changed);

        verify(delegate).save(changed);
        verify(userIdCache).remove(1001L);
        verify(userEmailCache).remove("old@example.com");
        verify(userEmailCache).remove("new@example.com");
        verify(emailContainCache).remove("old@example.com");
        verify(emailContainCache).remove("new@example.com");
    }

    private static ManagedUser managedUser(UserEmail email) {
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                email,
                new com.co.kc.imchat.service.account.domain.user.model.UserPassword("encrypted-password"),
                UserStatus.NORMAL,
                false,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));
        managedUser.setPkId(9L);
        return managedUser;
    }
}
