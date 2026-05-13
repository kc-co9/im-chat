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
import com.co.kc.imchat.domain.group.ImGroupMemberRepository;
import com.co.kc.imchat.domain.group.ImGroupRepository;
import com.co.kc.imchat.domain.group.ImGroupService;
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
import com.co.kc.imchat.support.notifier.ImMessageNotifierInvoker;
import com.co.kc.imchat.support.auth.PasswordService;
import com.co.kc.imchat.support.auth.TokenService;
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
                                       ImGroupRepository imGroupRepository,
                                       ImGroupChatRepository imGroupChatRepository,
                                       SessionRepository sessionRepository) {
        return new ImChatService(
                snowflakeId, friendRepository, imPrivateChatRepository, imGroupRepository, imGroupChatRepository, sessionRepository);
    }

    @Bean
    public ImMessageService imMessageService() {
        return new ImMessageService();
    }

    @Bean
    public ImGroupService imGroupService(ImGroupMemberRepository imGroupMemberRepository,
                                         ImGroupChatRepository imGroupChatRepository) {
        return new ImGroupService(imGroupMemberRepository, imGroupChatRepository);
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
                                             FriendService friendService) {
        return new FriendAppService(userRepository, friendRepository, friendService);
    }

    @Bean
    public ChatAppService chatAppService(SnowflakeId snowflakeId,
                                         ImPrivateChatRepository imPrivateChatRepository,
                                         ImGroupChatRepository imGroupChatRepository,
                                         ImGroupMemberRepository imGroupMemberRepository,
                                         ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                         FriendRepository friendRepository,
                                         ImChatService imChatService) {
        return new ChatAppService(
                snowflakeId, imPrivateChatRepository, imGroupChatRepository,
                imGroupMemberRepository, imGroupInboxMessageRepository, friendRepository, imChatService);
    }

    @Bean
    public GroupAppService groupAppService(SnowflakeId snowflakeId,
                                           ImGroupRepository imGroupRepository,
                                           ImGroupChatRepository imGroupChatRepository,
                                           ImGroupMemberRepository imGroupMemberRepository,
                                           ImGroupService imGroupService,
                                           ImChatService imChatService) {
        return new GroupAppService(
                snowflakeId, imGroupRepository, imGroupChatRepository,
                imGroupMemberRepository, imGroupService, imChatService);
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
    public GroupMessageAppService imGroupAppService(SnowflakeId snowflakeId,
                                                    ImGroupChatRepository imGroupChatRepository,
                                                    ImGroupMemberRepository imGroupMemberRepository,
                                                    ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                                    UserService userService,
                                                    ImGroupService imGroupService,
                                                    ImMessageService imMessageService,
                                                    ImMessageNotifierInvoker imMessageNotifierInvoker,
                                                    SpringEventPublisher imMessageEventPublisher) {
        return new GroupMessageAppService(
                snowflakeId, imGroupChatRepository, imGroupMemberRepository, imGroupInboxMessageRepository,
                userService, imGroupService, imMessageService, imMessageNotifierInvoker, imMessageEventPublisher);
    }
}
