package com.co.kc.imchat.infrastructure.config;

import com.co.kc.imchat.application.ChatAppService;
import com.co.kc.imchat.application.FriendAppService;
import com.co.kc.imchat.application.GroupAppService;
import com.co.kc.imchat.application.GroupMessageAppService;
import com.co.kc.imchat.application.PrivateMessageAppService;
import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.identity.snowflake.impl.StaticSnowflakeMachineId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.GroupMemberRepository;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.group.GroupService;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.friend.FriendService;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.domain.user.UserService;
import com.co.kc.imchat.infrastructure.support.SpringEventPublisher;
import com.co.kc.imchat.support.lock.aspect.DistributeLockAspect;
import com.co.kc.imchat.support.lock.client.RedisLockClient;
import com.co.kc.imchat.support.lock.template.DistributeLockTemplate;
import com.co.kc.imchat.support.notifier.ImMessageNotifierInvoker;
import com.co.kc.imchat.support.auth.PasswordService;
import com.co.kc.imchat.support.auth.TokenService;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public SnowflakeId snowflakeId() {
        return new SnowflakeId(new StaticSnowflakeMachineId(1, 1));
    }

    @Bean
    public UserService userService(SnowflakeId snowflakeId,
                                   UserRepository userRepository,
                                   SessionRepository sessionRepository,
                                   PasswordService passwordService) {
        return new UserService(snowflakeId, userRepository, sessionRepository, passwordService);
    }

    @Bean
    public FriendService friendService() {
        return new FriendService();
    }

    @Bean
    public ImChatService imChatService(SnowflakeId snowflakeId,
                                       FriendRepository friendRepository,
                                       ImPrivateChatRepository imPrivateChatRepository,
                                       GroupRepository groupRepository,
                                       ImGroupChatRepository imGroupChatRepository,
                                       SessionRepository sessionRepository) {
        return new ImChatService(
                snowflakeId, friendRepository, imPrivateChatRepository, groupRepository, imGroupChatRepository, sessionRepository);
    }

    @Bean
    public ImMessageService imMessageService(SnowflakeId snowflakeId) {
        return new ImMessageService(snowflakeId);
    }

    @Bean
    public GroupService groupService(GroupMemberRepository groupMemberRepository,
                                     ImGroupChatRepository imGroupChatRepository,
                                     FriendRepository friendRepository,
                                     UserRepository userRepository) {
        return new GroupService(groupMemberRepository, imGroupChatRepository, friendRepository, userRepository);
    }

    @Bean
    public UserAppService userAppService(UserRepository userRepository,
                                         SessionRepository sessionRepository,
                                         UserService userService,
                                         TokenService tokenService) {
        return new UserAppService(userRepository, sessionRepository, userService, tokenService);
    }

    @Bean
    public FriendAppService friendAppService(UserRepository userRepository,
                                             FriendRepository friendRepository,
                                             ImPrivateChatRepository imPrivateChatRepository,
                                             FriendService friendService,
                                             ImChatService imChatService) {
        return new FriendAppService(userRepository, friendRepository, imPrivateChatRepository, friendService, imChatService);
    }

    @Bean
    public ChatAppService chatAppService(ImPrivateChatRepository imPrivateChatRepository,
                                         ImGroupChatRepository imGroupChatRepository,
                                         GroupMemberRepository groupMemberRepository,
                                         ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                         GroupRepository groupRepository,
                                         FriendRepository friendRepository,
                                         ImChatService imChatService) {
        return new ChatAppService(
                imPrivateChatRepository, imGroupChatRepository,
                groupMemberRepository, imGroupInboxMessageRepository, groupRepository,
                friendRepository, imChatService);
    }

    @Bean
    public GroupAppService groupAppService(SnowflakeId snowflakeId,
                                           GroupRepository groupRepository,
                                           ImGroupChatRepository imGroupChatRepository,
                                           GroupMemberRepository groupMemberRepository,
                                           GroupService groupService,
                                           ImChatService imChatService,
                                           ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                           ImMessageService imMessageService,
                                           SpringEventPublisher imMessageEventPublisher) {
        return new GroupAppService(
                snowflakeId, groupRepository, imGroupChatRepository,
                groupMemberRepository, groupService, imChatService,
                imGroupInboxMessageRepository, imMessageService, imMessageEventPublisher);
    }

    @Bean
    public PrivateMessageAppService privateMessageAppService(SnowflakeId snowflakeId,
                                                   ImPrivateChatRepository imPrivateChatRepository,
                                                   ImPrivateInboxMessageRepository imPrivateInboxMessageRepository,
                                                   UserService userService,
                                                   ImChatService imChatService,
                                                   ImMessageService imMessageService,
                                                   ImMessageNotifierInvoker imMessageNotifierInvoker,
                                                   SpringEventPublisher imMessageEventPublisher) {
        return new PrivateMessageAppService(
                snowflakeId, imPrivateChatRepository, imPrivateInboxMessageRepository,
                userService, imChatService, imMessageService, imMessageNotifierInvoker,
                imMessageEventPublisher);
    }

    @Bean
    public GroupMessageAppService groupMessageAppService(SnowflakeId snowflakeId,
                                                         ImGroupChatRepository imGroupChatRepository,
                                                         GroupMemberRepository groupMemberRepository,
                                                         ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                                         GroupRepository groupRepository,
                                                         UserService userService,
                                                         GroupService groupService,
                                                         ImMessageService imMessageService,
                                                         ImMessageNotifierInvoker imMessageNotifierInvoker,
                                                         SpringEventPublisher imMessageEventPublisher) {
        return new GroupMessageAppService(
                snowflakeId, imGroupChatRepository, groupMemberRepository, imGroupInboxMessageRepository,
                groupRepository, userService, groupService, imMessageService, imMessageNotifierInvoker,
                imMessageEventPublisher);
    }

    @Bean
    public RedisLockClient redisLockClient(RedissonClient redissonClient) {
        return new RedisLockClient(redissonClient);
    }

    @Bean
    public DistributeLockTemplate distributeLockTemplate(RedisLockClient redisLockClient) {
        return new DistributeLockTemplate(redisLockClient);
    }

    @Bean
    public DistributeLockAspect distributeLockAspect(DistributeLockTemplate distributeLockTemplate) {
        return new DistributeLockAspect(distributeLockTemplate);
    }

}
