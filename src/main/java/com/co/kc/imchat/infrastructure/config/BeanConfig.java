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
                                             ImChatService imChatService,
                                             SpringEventPublisher domainEventPublisher) {
        return new FriendAppService(
                userRepository, friendRepository, imPrivateChatRepository,
                friendService, imChatService, domainEventPublisher);
    }

    @Bean
    public ChatAppService chatAppService(ImPrivateChatRepository imPrivateChatRepository,
                                         ImGroupChatRepository imGroupChatRepository,
                                         GroupRepository groupRepository,
                                         GroupService groupService,
                                         ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                         ImChatService imChatService,
                                         FriendService friendService,
                                         ImMessageService imMessageService) {
        return new ChatAppService(
                imPrivateChatRepository, imGroupChatRepository,
                groupRepository, groupService,
                imGroupInboxMessageRepository, imChatService,
                friendService, imMessageService);
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
                groupRepository, imGroupChatRepository,
                groupMemberRepository, imGroupInboxMessageRepository,
                groupService, imChatService, imMessageService,
                snowflakeId, imMessageEventPublisher);
    }

    @Bean
    public PrivateMessageAppService privateMessageAppService(SnowflakeId snowflakeId,
                                                             ImPrivateChatRepository imPrivateChatRepository,
                                                             ImPrivateInboxMessageRepository imPrivateInboxMessageRepository,
                                                             UserService userService,
                                                             ImChatService imChatService,
                                                             ImMessageService imMessageService,
                                                             FriendService friendService,
                                                             ImMessageNotifierInvoker imMessageNotifierInvoker,
                                                             SpringEventPublisher imMessageEventPublisher) {
        return new PrivateMessageAppService(
                imPrivateChatRepository, imPrivateInboxMessageRepository,
                userService, imChatService, imMessageService, friendService,
                snowflakeId, imMessageNotifierInvoker,
                imMessageEventPublisher);
    }

    @Bean
    public GroupMessageAppService groupMessageAppService(SnowflakeId snowflakeId,
                                                         ImGroupChatRepository imGroupChatRepository,
                                                         GroupRepository groupRepository,
                                                         GroupMemberRepository groupMemberRepository,
                                                         ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                                         UserService userService,
                                                         GroupService groupService,
                                                         ImMessageService imMessageService,
                                                         ImChatService imChatService,
                                                         ImMessageNotifierInvoker imMessageNotifierInvoker,
                                                         SpringEventPublisher imMessageEventPublisher) {
        return new GroupMessageAppService(
                imGroupChatRepository, groupRepository,
                groupMemberRepository, imGroupInboxMessageRepository,
                userService, groupService, imMessageService, imChatService,
                snowflakeId, imMessageNotifierInvoker, imMessageEventPublisher);
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
