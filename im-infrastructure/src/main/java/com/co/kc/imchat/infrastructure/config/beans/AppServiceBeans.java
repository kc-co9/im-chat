package com.co.kc.imchat.infrastructure.config.beans;

import com.co.kc.imchat.application.ChatAppService;
import com.co.kc.imchat.application.FriendAppService;
import com.co.kc.imchat.application.GroupAppService;
import com.co.kc.imchat.application.GroupMessageAppService;
import com.co.kc.imchat.application.NotificationAckAppService;
import com.co.kc.imchat.application.PrivateMessageAppService;
import com.co.kc.imchat.application.UserAppService;
import com.co.kc.imchat.application.support.auth.TokenService;
import com.co.kc.imchat.application.support.notifier.ImMessageNotifierInvoker;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableService;
import com.co.kc.imchat.application.support.notifier.receiver.NotificationAckReceiver;
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
import com.co.kc.imchat.domain.user.service.UserService;
import com.co.kc.imchat.infrastructure.support.SpringEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AppServiceBeans {

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
    public NotificationAckAppService notificationAckAppService(ImMessageConfirmableService imMessageConfirmableService,
                                                               List<NotificationAckReceiver> notificationAckReceivers) {
        return new NotificationAckAppService(imMessageConfirmableService, notificationAckReceivers);
    }
}
