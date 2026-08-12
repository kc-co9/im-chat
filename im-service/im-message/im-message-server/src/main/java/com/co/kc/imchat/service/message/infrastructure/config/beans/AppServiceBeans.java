package com.co.kc.imchat.service.message.infrastructure.config.beans;

import com.co.kc.imchat.service.message.application.ChatAppService;
import com.co.kc.imchat.service.message.application.GroupMessageAppService;
import com.co.kc.imchat.service.message.application.NotificationAckAppService;
import com.co.kc.imchat.service.message.application.PrivateMessageAppService;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierInvoker;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableService;
import com.co.kc.imchat.service.message.application.notification.receiver.NotificationAckReceiver;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.service.message.domain.chat.service.ImChatService;
import com.co.kc.imchat.service.message.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.repository.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.service.ImMessageService;
import com.co.kc.imchat.service.message.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AppServiceBeans {

    @Bean
    public ChatAppService chatAppService(ImPrivateChatRepository imPrivateChatRepository,
                                         ImGroupChatRepository imGroupChatRepository,
                                         ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                         ImChatService imChatService,
                                         SocialAdapter socialAdapter,
                                         ImMessageService imMessageService,
                                         DomainEventPublisher imMessageEventPublisher) {
        return new ChatAppService(
                imPrivateChatRepository, imGroupChatRepository,
                imGroupInboxMessageRepository, imChatService,
                socialAdapter, imMessageService,
                imMessageEventPublisher);
    }

    @Bean
    public PrivateMessageAppService privateMessageAppService(SnowflakeId snowflakeId,
                                                             ImPrivateChatRepository imPrivateChatRepository,
                                                             ImPrivateInboxMessageRepository imPrivateInboxMessageRepository,
                                                             AccountAdapter accountAdapter,
                                                             ImChatService imChatService,
                                                             ImMessageService imMessageService,
                                                             SocialAdapter socialAdapter,
                                                             ImMessageNotifierInvoker imMessageNotifierInvoker,
                                                             DomainEventPublisher imMessageEventPublisher,
                                                             PlatformTransactionManager transactionManager) {
        return new PrivateMessageAppService(
                imPrivateChatRepository, imPrivateInboxMessageRepository,
                accountAdapter, imChatService, imMessageService, socialAdapter,
                snowflakeId, imMessageNotifierInvoker,
                imMessageEventPublisher, new TransactionTemplate(transactionManager));
    }

    @Bean
    public GroupMessageAppService groupMessageAppService(SnowflakeId snowflakeId,
                                                         ImGroupChatRepository imGroupChatRepository,
                                                         ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                                         AccountAdapter accountAdapter,
                                                         SocialAdapter socialAdapter,
                                                         ImMessageService imMessageService,
                                                         ImChatService imChatService,
                                                         ImMessageNotifierInvoker imMessageNotifierInvoker,
                                                         DomainEventPublisher imMessageEventPublisher,
                                                         PlatformTransactionManager transactionManager) {
        return new GroupMessageAppService(
                imGroupChatRepository, imGroupInboxMessageRepository,
                accountAdapter, socialAdapter, imMessageService, imChatService,
                snowflakeId, imMessageNotifierInvoker, imMessageEventPublisher,
                new TransactionTemplate(transactionManager));
    }

    @Bean
    public NotificationAckAppService notificationAckAppService(ImMessageConfirmableService imMessageConfirmableService,
                                                               List<NotificationAckReceiver> notificationAckReceivers) {
        return new NotificationAckAppService(imMessageConfirmableService, notificationAckReceivers);
    }

}
