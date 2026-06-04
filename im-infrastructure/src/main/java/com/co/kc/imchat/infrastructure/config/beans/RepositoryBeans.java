package com.co.kc.imchat.infrastructure.config.beans;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.domain.friend.model.Friend;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.infrastructure.domain.repository.CachedFriendRepository;
import com.co.kc.imchat.infrastructure.domain.repository.CachedGroupMemberRepository;
import com.co.kc.imchat.infrastructure.domain.repository.CachedGroupRepository;
import com.co.kc.imchat.infrastructure.domain.repository.CachedUserRepository;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlFriendRepository;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlGroupMemberRepository;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlGroupRepository;
import com.co.kc.imchat.infrastructure.domain.repository.MysqlUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RepositoryBeans {

    @Bean
    @Primary
    public UserRepository userRepository(MysqlUserRepository mysqlUserRepository, Cache<Long, User> userIdCache,
                                         Cache<String, User> userEmailCache,
                                         Cache<String, Boolean> userEmailContainCache) {
        return new CachedUserRepository(mysqlUserRepository, userIdCache, userEmailCache, userEmailContainCache);
    }

    @Bean
    @Primary
    public GroupRepository groupRepository(MysqlGroupRepository mysqlGroupRepository, Cache<Long, Group> groupIdCache) {
        return new CachedGroupRepository(mysqlGroupRepository, groupIdCache);
    }

    @Bean
    @Primary
    public GroupMemberRepository groupMemberRepository(MysqlGroupMemberRepository mysqlGroupMemberRepository,
                                                       Cache<String, GroupMember> groupMemberCache) {
        return new CachedGroupMemberRepository(mysqlGroupMemberRepository, groupMemberCache);
    }

    @Bean
    @Primary
    public FriendRepository friendRepository(MysqlFriendRepository mysqlFriendRepository,
                                             Cache<String, Friend> friendEdgeCache) {
        return new CachedFriendRepository(mysqlFriendRepository, friendEdgeCache);
    }
}
