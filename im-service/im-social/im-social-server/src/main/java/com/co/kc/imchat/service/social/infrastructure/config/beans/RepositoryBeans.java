package com.co.kc.imchat.service.social.infrastructure.config.beans;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.service.social.domain.friend.model.Friend;
import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.service.social.domain.group.model.Group;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import com.co.kc.imchat.service.social.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.service.social.domain.group.repository.GroupRepository;
import com.co.kc.imchat.service.social.infrastructure.domain.repository.CachedFriendRepository;
import com.co.kc.imchat.service.social.infrastructure.domain.repository.CachedGroupMemberRepository;
import com.co.kc.imchat.service.social.infrastructure.domain.repository.CachedGroupRepository;
import com.co.kc.imchat.service.social.infrastructure.domain.repository.MysqlFriendRepository;
import com.co.kc.imchat.service.social.infrastructure.domain.repository.MysqlGroupMemberRepository;
import com.co.kc.imchat.service.social.infrastructure.domain.repository.MysqlGroupRepository;
import com.co.kc.imchat.service.social.infrastructure.mybatis.service.DbFriendService;
import com.co.kc.imchat.service.social.infrastructure.mybatis.service.DbImGroupService;
import com.co.kc.imchat.service.social.infrastructure.mybatis.service.DbImGroupMemberService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 社交仓储 Bean 装配。
 */
@Configuration
public class RepositoryBeans {

    @Bean
    public DbFriendService dbFriendService() {
        return new DbFriendService();
    }

    @Bean
    public DbImGroupService dbImGroupService() {
        return new DbImGroupService();
    }

    @Bean
    public DbImGroupMemberService dbImGroupMemberService() {
        return new DbImGroupMemberService();
    }

    @Bean
    public MysqlFriendRepository mysqlFriendRepository(DbFriendService dbFriendService) {
        return new MysqlFriendRepository(dbFriendService);
    }

    @Bean
    public MysqlGroupRepository mysqlGroupRepository(DbImGroupService dbImGroupService) {
        return new MysqlGroupRepository(dbImGroupService);
    }

    @Bean
    public MysqlGroupMemberRepository mysqlGroupMemberRepository(DbImGroupMemberService dbImGroupMemberService) {
        return new MysqlGroupMemberRepository(dbImGroupMemberService);
    }

    @Bean
    @Primary
    public FriendRepository friendRepository(MysqlFriendRepository mysqlFriendRepository,
                                             Cache<String, Friend> friendEdgeCache) {
        return new CachedFriendRepository(mysqlFriendRepository, friendEdgeCache);
    }

    @Bean
    @Primary
    public GroupRepository groupRepository(MysqlGroupRepository mysqlGroupRepository,
                                           Cache<Long, Group> groupIdCache) {
        return new CachedGroupRepository(mysqlGroupRepository, groupIdCache);
    }

    @Bean
    @Primary
    public GroupMemberRepository groupMemberRepository(MysqlGroupMemberRepository mysqlGroupMemberRepository,
                                                       Cache<String, GroupMember> groupMemberCache) {
        return new CachedGroupMemberRepository(mysqlGroupMemberRepository, groupMemberCache);
    }
}
