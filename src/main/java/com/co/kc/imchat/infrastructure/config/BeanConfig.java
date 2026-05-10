package com.co.kc.imchat.infrastructure.config;

import com.co.kc.imchat.application.ChatAppService;
import com.co.kc.imchat.application.FriendAppService;
import com.co.kc.imchat.application.ImGroupAppService;
import com.co.kc.imchat.application.ImPrivateAppService;
import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.identity.snowflake.impl.StaticSnowflakeMachineId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.friend.FriendService;
import com.co.kc.imchat.domain.message.ImGroupMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.domain.user.UserService;
import com.co.kc.imchat.infrastructure.support.SpringEventPublisher;
import com.co.kc.imchat.support.ImMessageNotifier;
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
    public ImChatService imChatService(FriendRepository friendRepository,
                                       ImPrivateChatRepository imPrivateChatRepository,
                                       ImGroupChatRepository imGroupChatRepository,
                                       SessionRepository sessionRepository) {
        return new ImChatService(friendRepository, imPrivateChatRepository, imGroupChatRepository, sessionRepository);
    }

    @Bean
    public ImMessageService imMessageService() {
        return new ImMessageService();
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
                                         FriendRepository friendRepository,
                                         ImChatService imChatService) {
        return new ChatAppService(
                snowflakeId, imPrivateChatRepository, imGroupChatRepository, friendRepository, imChatService);
    }

    @Bean
    public ImPrivateAppService imPrivateAppService(SnowflakeId snowflakeId,
                                                   ImPrivateChatRepository imPrivateChatRepository,
                                                   ImPrivateInboxMessageRepository imPrivateInboxMessageRepository,
                                                   UserService userService,
                                                   ImChatService imChatService,
                                                   ImMessageService imMessageService,
                                                   ImMessageNotifier imMessageNotifier,
                                                   SpringEventPublisher imMessageEventPublisher) {
        return new ImPrivateAppService(
                snowflakeId, imPrivateChatRepository, imPrivateInboxMessageRepository,
                userService, imChatService, imMessageService, imMessageNotifier, imMessageEventPublisher);
    }

    @Bean
    public ImGroupAppService imGroupAppService(SnowflakeId snowflakeId,
                                               ImGroupChatRepository imGroupChatRepository,
                                               ImGroupMessageRepository imGroupMessageRepository,
                                               ImMessageService imMessageService,
                                               ImMessageNotifier imMessageNotifier,
                                               SpringEventPublisher imMessageEventPublisher) {
        return new ImGroupAppService(
                snowflakeId, imGroupChatRepository, imGroupMessageRepository,
                imMessageService, imMessageNotifier, imMessageEventPublisher);
    }
}
