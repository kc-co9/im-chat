package com.co.kc.imchat.infrastructure.domain;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.domain.chat.model.ImChatType;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupName;
import com.co.kc.imchat.domain.group.model.GroupStatus;
import com.co.kc.imchat.domain.group.model.MemberCount;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.infrastructure.domain.repository.CachedGroupRepository;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlGroupRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedGroupRepositoryTest {

    private final MysqlGroupRepository delegate = mock(MysqlGroupRepository.class);
    private final Cache<Long, Group> groupIdCache = mock(Cache.class);
    private final CachedGroupRepository repository = new CachedGroupRepository(delegate, groupIdCache);

    @Test
    void findByIdReturnsCachedGroupWithoutDelegating() {
        Group group = group();
        when(groupIdCache.get(group.getId().value())).thenReturn(group);

        Optional<Group> result = repository.find(group.getId());

        assertThat(result).contains(group);
        verify(delegate, never()).find(group.getId());
    }

    @Test
    void findByIdBackfillsCacheWhenFound() {
        Group group = group();
        when(delegate.find(group.getId())).thenReturn(Optional.of(group));

        assertThat(repository.find(group.getId())).contains(group);

        verify(delegate).find(group.getId());
        verify(groupIdCache).put(group.getId().value(), group);
    }

    @Test
    void saveDelegatesToMysqlRepository() {
        Group group = group();

        repository.save(group);

        verify(delegate).save(group);
    }

    private Group group() {
        return Group.builder()
                .id(new GroupId(1001L))
                .type(ImChatType.GROUP)
                .ownerId(new UserId(1L))
                .name(new GroupName("group"))
                .memberCount(new MemberCount(2))
                .status(GroupStatus.ACTIVE)
                .build();
    }
}
