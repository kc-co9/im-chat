package com.co.kc.imchat.service.social.infrastructure.config.beans;

import com.co.kc.imchat.service.social.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.service.social.domain.friend.service.FriendService;
import com.co.kc.imchat.service.social.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.service.social.domain.group.repository.GroupRepository;
import com.co.kc.imchat.service.social.domain.group.service.GroupService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 社交领域服务 Bean 装配。
 */
@Configuration
public class DomainServiceBeans {

    @Bean
    public FriendService friendService(FriendRepository friendRepository) {
        return new FriendService(friendRepository);
    }

    @Bean
    public GroupService groupService(GroupMemberRepository groupMemberRepository,
                                     GroupRepository groupRepository) {
        return new GroupService(groupMemberRepository, groupRepository);
    }
}
