package com.co.kc.imchat.service.message.infrastructure.config.beans;

import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.service.message.domain.chat.service.ImChatService;
import com.co.kc.imchat.service.message.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.repository.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.service.ImMessageService;
import com.co.kc.imchat.service.message.domain.chat.repository.ImChatViewRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceBeans {

    @Bean
    public ImChatService imChatService(SnowflakeId snowflakeId,
                                       ImPrivateChatRepository imPrivateChatRepository,
                                       ImGroupChatRepository imGroupChatRepository) {
        return new ImChatService(
                imPrivateChatRepository, imGroupChatRepository, snowflakeId);
    }

    @Bean
    public ImMessageService imMessageService(SnowflakeId snowflakeId,
                                             ImGroupInboxMessageRepository imGroupInboxMessageRepository,
                                             ImPrivateInboxMessageRepository imPrivateInboxMessageRepository) {
        return new ImMessageService(imGroupInboxMessageRepository, imPrivateInboxMessageRepository, snowflakeId);
    }

}
