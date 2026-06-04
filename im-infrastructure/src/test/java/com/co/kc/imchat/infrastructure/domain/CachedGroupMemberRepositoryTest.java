package com.co.kc.imchat.infrastructure.domain;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.infrastructure.domain.repository.CachedGroupMemberRepository;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlGroupMemberRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedGroupMemberRepositoryTest {

    private final MysqlGroupMemberRepository delegate = mock(MysqlGroupMemberRepository.class);
    private final Cache<String, GroupMember> groupMemberCache = mock(Cache.class);
    private final CachedGroupMemberRepository repository = new CachedGroupMemberRepository(delegate, groupMemberCache);

    @Test
    void findByGroupCachesNonEmptyMembersOnly() {
        GroupId groupId = new GroupId(1001L);
        List<GroupMember> members = List.of(member(groupId, new UserId(1L)));
        when(delegate.find(groupId)).thenReturn(members);

        List<GroupMember> result = repository.find(groupId);

        assertThat(result).isEqualTo(members);
        verify(delegate).find(groupId);
    }

    @Test
    void findSingleMemberReturnsCachedMemberWithoutDelegating() {
        GroupId groupId = new GroupId(1001L);
        UserId userId = new UserId(1L);
        GroupMember member = member(groupId, userId);
        when(groupMemberCache.get("1001:1")).thenReturn(member);

        Optional<GroupMember> result = repository.find(groupId, userId);

        assertThat(result).contains(member);
        verify(delegate, never()).find(groupId, userId);
    }

    @Test
    void findSingleMemberBackfillsCacheWhenFound() {
        GroupId groupId = new GroupId(1001L);
        UserId userId = new UserId(1L);
        GroupMember member = member(groupId, userId);
        when(delegate.find(groupId, userId)).thenReturn(Optional.of(member));

        assertThat(repository.find(groupId, userId)).contains(member);

        verify(delegate).find(groupId, userId);
        verify(groupMemberCache).put("1001:1", member);
    }

    @Test
    void saveBatchDelegatesToMysqlRepository() {
        GroupMember first = member(new GroupId(1001L), new UserId(1L));
        GroupMember second = member(new GroupId(1001L), new UserId(2L));
        List<GroupMember> members = List.of(first, second);

        repository.save(members);

        verify(delegate).save(members);
    }

    private GroupMember member(GroupId groupId, UserId userId) {
        return GroupMember.builder()
                .id(new MemberId(groupId, userId))
                .groupId(groupId)
                .userId(userId)
                .joinTime(LocalDateTime.now())
                .build();
    }
}
