package com.co.kc.imchat.infrastructure.domain;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.domain.user.model.UserPassword;
import com.co.kc.imchat.infrastructure.domain.repository.CachedUserRepository;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlUserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedUserRepositoryTest {

    private final MysqlUserRepository delegate = mock(MysqlUserRepository.class);
    private final Cache<Long, User> userIdCache = mock(Cache.class);
    private final Cache<String, User> userEmailCache = mock(Cache.class);
    private final Cache<String, Boolean> userEmailContainCache = mock(Cache.class);
    private final CachedUserRepository repository =
            new CachedUserRepository(delegate, userIdCache, userEmailCache, userEmailContainCache);

    @Test
    void findByIdReturnsCachedUserWithoutDelegating() {
        User user = user();
        when(userIdCache.get(user.getId().value())).thenReturn(user);

        Optional<User> result = repository.find(user.getId());

        assertThat(result).contains(user);
        verify(delegate, never()).find(user.getId());
    }

    @Test
    void findByIdBackfillsCacheWhenFound() {
        User user = user();
        when(delegate.find(user.getId())).thenReturn(Optional.of(user));

        assertThat(repository.find(user.getId())).contains(user);

        verify(delegate).find(user.getId());
        verify(userIdCache).put(user.getId().value(), user);
    }

    @Test
    void findByEmailReturnsCachedUserWithoutDelegating() {
        User user = user();
        when(userEmailCache.get(user.getEmail().value())).thenReturn(user);

        Optional<User> result = repository.find(user.getEmail());

        assertThat(result).contains(user);
        verify(delegate, never()).find(user.getEmail());
    }

    @Test
    void findByEmailBackfillsCacheWhenFound() {
        User user = user();
        when(delegate.find(user.getEmail())).thenReturn(Optional.of(user));

        assertThat(repository.find(user.getEmail())).contains(user);

        verify(delegate).find(user.getEmail());
        verify(userEmailCache).put(user.getEmail().value(), user);
    }

    @Test
    void containDelegatesExistenceCheck() {
        UserEmail email = new UserEmail("one@example.com");
        when(delegate.contain(email)).thenReturn(true);

        assertThat(repository.contain(email)).isTrue();

        verify(delegate).contain(email);
    }

    @Test
    void saveDelegatesToMysqlRepository() {
        User user = user();

        repository.save(user);

        verify(delegate).save(user);
    }

    @Test
    void saveRemovesPreviousEmailCacheWhenEmailChanged() {
        User previous = user();
        User changed = new User(previous.getId(), new UserEmail("two@example.com"),
                previous.getUsername(), previous.getPassword());
        when(delegate.find(changed.getId())).thenReturn(Optional.of(previous));

        repository.save(changed);

        verify(userEmailCache).remove(previous.getEmail().value());
        verify(userEmailContainCache).remove(previous.getEmail().value());
        verify(delegate).save(changed);
    }

    private User user() {
        return new User(new UserId(1L), new UserEmail("one@example.com"), new UserName("one"), new UserPassword("encoded"));
    }
}
