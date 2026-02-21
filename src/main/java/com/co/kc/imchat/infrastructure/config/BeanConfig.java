package com.co.kc.imchat.infrastructure.config;

import com.co.kc.imchat.application.ChatAppService;
import com.co.kc.imchat.application.FriendAppService;
import com.co.kc.imchat.application.ImGroupAppService;
import com.co.kc.imchat.application.ImPrivateAppService;
import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.identity.snowflake.impl.StaticSnowflakeMachineId;
import com.co.kc.imchat.domain.chat.ImChatRepository;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.friend.FriendService;
import com.co.kc.imchat.domain.message.ImGroupMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImPrivateMessageRepository;
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
                                       ImChatRepository imChatRepository,
                                       SessionRepository sessionRepository) {
        return new ImChatService(friendRepository, imChatRepository, sessionRepository);
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
                                         ImChatRepository imChatRepository,
                                         FriendRepository friendRepository,
                                         ImChatService imChatService) {
        return new ChatAppService(snowflakeId, imChatRepository, friendRepository, imChatService);
    }

    @Bean
    public ImPrivateAppService imPrivateAppService(SnowflakeId snowflakeId,
                                                   ImChatRepository imChatRepository,
                                                   ImPrivateMessageRepository imPrivateMessageRepository,
                                                   UserService userService,
                                                   ImChatService imChatService,
                                                   ImMessageService imMessageService,
                                                   ImMessageNotifier imMessageNotifier,
                                                   SpringEventPublisher imMessageEventPublisher) {
        return new ImPrivateAppService(
                snowflakeId, imChatRepository, imPrivateMessageRepository,
                userService, imChatService, imMessageService, imMessageNotifier, imMessageEventPublisher);
    }

    @Bean
    public ImGroupAppService imGroupAppService(SnowflakeId snowflakeId,
                                               ImChatRepository imChatRepository,
                                               ImGroupMessageRepository imGroupMessageRepository,
                                               ImMessageService imMessageService,
                                               ImMessageNotifier imMessageNotifier,
                                               SpringEventPublisher imMessageEventPublisher) {
        return new ImGroupAppService(snowflakeId, imChatRepository, imGroupMessageRepository, imMessageService, imMessageNotifier, imMessageEventPublisher);
    }
}
