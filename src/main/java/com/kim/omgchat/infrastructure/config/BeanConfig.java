package com.kim.omgchat.infrastructure.config;

import com.kim.omgchat.application.ChatAppService;
import com.kim.omgchat.application.FriendAppService;
import com.kim.omgchat.application.PrivateAppService;
import com.kim.omgchat.application.UserAppService;
import com.kim.omgchat.common.identity.snowflake.SnowflakeId;
import com.kim.omgchat.common.identity.snowflake.impl.StaticSnowflakeMachineId;
import com.kim.omgchat.domain.chat.ImChatRepository;
import com.kim.omgchat.domain.chat.ImChatService;
import com.kim.omgchat.domain.friend.FriendRepository;
import com.kim.omgchat.domain.friend.FriendService;
import com.kim.omgchat.domain.message.ImMessageService;
import com.kim.omgchat.domain.message.ImPrivateMessageRepository;
import com.kim.omgchat.domain.session.SessionRepository;
import com.kim.omgchat.domain.user.UserRepository;
import com.kim.omgchat.domain.user.UserService;
import com.kim.omgchat.infrastructure.support.SpringEventPublisher;
import com.kim.omgchat.support.ImMessageNotifier;
import com.kim.omgchat.support.user.PasswordService;
import com.kim.omgchat.support.user.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public SnowflakeId snowflakeId() {
        return new SnowflakeId(new StaticSnowflakeMachineId(1, 1));
    }

    @Bean
    public UserService userService(UserRepository userRepository,
                                   PasswordService passwordService) {
        return new UserService(userRepository, passwordService);
    }

    @Bean
    public FriendService friendService() {
        return new FriendService();
    }

    @Bean
    public ImChatService imChatService(UserRepository userRepository) {
        return new ImChatService(userRepository);
    }

    @Bean
    public ImMessageService imMessageService() {
        return new ImMessageService();
    }

    @Bean
    public UserAppService userAppService(SnowflakeId snowflakeId,
                                         UserRepository userRepository,
                                         SessionRepository sessionRepository,
                                         UserService userService,
                                         TokenService tokenService,
                                         PasswordService passwordService) {
        return new UserAppService(snowflakeId, userRepository, sessionRepository, userService, tokenService, passwordService);
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
                                         SessionRepository sessionRepository,
                                         ImChatService imChatService) {
        return new ChatAppService(snowflakeId, imChatRepository, friendRepository, sessionRepository, imChatService);
    }

    @Bean
    public PrivateAppService privateAppService(SnowflakeId snowflakeId,
                                               ImChatRepository imChatRepository,
                                               ImPrivateMessageRepository imPrivateMessageRepository,
                                               UserService userService,
                                               ImChatService imChatService,
                                               ImMessageService imMessageService,
                                               ImMessageNotifier imMessageNotifier,
                                               SpringEventPublisher imMessageEventPublisher) {
        return new PrivateAppService(
                snowflakeId, imChatRepository, imPrivateMessageRepository,
                userService, imChatService, imMessageService, imMessageNotifier, imMessageEventPublisher);
    }
}
