package com.co.kc.imchat.infrastructure.config.beans;

import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.domain.chat.service.ImChatService;
import com.co.kc.imchat.domain.friend.repository.FriendRepository;
import com.co.kc.imchat.domain.friend.service.FriendService;
import com.co.kc.imchat.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.group.service.GroupService;
import com.co.kc.imchat.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.repository.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.domain.message.service.ImMessageService;
import com.co.kc.imchat.domain.session.repository.SessionRepository;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.domain.user.service.PasswordService;
import com.co.kc.imchat.domain.user.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceBeans {

    @Bean
    public UserService userService(SnowflakeId snowflakeId,
                                   UserRepository userRepository,
                                   SessionRepository sessionRepository,
                                   PasswordService passwordService) {
        return new UserService(userRepository, sessionRepository, passwordService, snowflakeId);
    }

    @Bean
    public FriendService friendService(FriendRepository friendRepository) {
        return new FriendService(friendRepository);
    }

    @Bean
    public ImChatService imChatService(SnowflakeId snowflakeId,
                                       FriendRepository friendRepository,
                                       ImPrivateChatRepository imPrivateChatRepository,
                                       GroupRepository groupRepository,
                                       ImGroupChatRepository imGroupChatRepository,
                                       SessionRepository sessionRepository) {
        return new ImChatService(
                friendRepository, imPrivateChatRepository, groupRepository, imGroupChatRepository, sessionRepository, snowflakeId);
    }

    @Bean
    public ImMessageService imMessageService(SnowflakeId snowflakeId,
                                             ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                             ImPrivateInboxMessageRepository imPrivateInboxMessageRepository) {
        return new ImMessageService(imGroupInboxMessageRepository, imPrivateInboxMessageRepository, snowflakeId);
    }

    @Bean
    public GroupService groupService(GroupMemberRepository groupMemberRepository,
                                     ImGroupChatRepository imGroupChatRepository,
                                     GroupRepository groupRepository,
                                     FriendRepository friendRepository,
                                     UserRepository userRepository) {
        return new GroupService(
                groupMemberRepository, imGroupChatRepository, groupRepository, friendRepository, userRepository);
    }
}
