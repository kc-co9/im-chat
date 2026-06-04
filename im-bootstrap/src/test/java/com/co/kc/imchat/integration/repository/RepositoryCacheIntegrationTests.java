package com.co.kc.imchat.integration.repository;

import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.model.GroupUserAlias;
import com.co.kc.imchat.domain.group.model.MemberId;
import com.co.kc.imchat.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlGroupMemberRepository;
import com.co.kc.imchat.support.ImChatSpringBootTest;
import com.co.kc.imchat.support.TestCacheConfiguration;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ImChatSpringBootTest
@Import(TestCacheConfiguration.class)
class RepositoryCacheIntegrationTests {

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @MockitoBean
    private MysqlGroupMemberRepository mysqlGroupMemberRepository;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Test
    void annotationCacheCachesGroupMembersAndInvalidatesAfterSave() {
        GroupId groupId = new GroupId(1001L);
        GroupMember first = member(groupId, new UserId(1L));
        GroupMember second = member(groupId, new UserId(2L));
        when(mysqlGroupMemberRepository.find(groupId))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first, second));

        assertThat(groupMemberRepository.find(groupId)).containsExactly(first);
        assertThat(groupMemberRepository.find(groupId)).containsExactly(first);

        groupMemberRepository.save(second);

        assertThat(groupMemberRepository.find(groupId)).containsExactly(first, second);
        verify(mysqlGroupMemberRepository, times(2)).find(groupId);
        verify(mysqlGroupMemberRepository).save(second);
    }

    @Test
    void annotationInvalidateRemovesProgrammaticSingleMemberCache() {
        GroupId groupId = new GroupId(2001L);
        UserId userId = new UserId(1L);
        GroupMember first = member(groupId, userId);
        GroupMember changed = member(groupId, userId);
        changed.changeUserAlias(new GroupUserAlias("new-alias"));
        when(mysqlGroupMemberRepository.find(groupId, userId))
                .thenReturn(Optional.of(first))
                .thenReturn(Optional.of(changed));

        assertThat(groupMemberRepository.find(groupId, userId)).contains(first);
        assertThat(groupMemberRepository.find(groupId, userId)).contains(first);

        groupMemberRepository.save(changed);

        assertThat(groupMemberRepository.find(groupId, userId)).contains(changed);
        verify(mysqlGroupMemberRepository, times(2)).find(groupId, userId);
        verify(mysqlGroupMemberRepository).save(changed);
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
