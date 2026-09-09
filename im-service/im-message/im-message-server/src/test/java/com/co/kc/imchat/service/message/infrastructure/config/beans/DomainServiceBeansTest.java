package com.co.kc.imchat.service.message.infrastructure.config.beans;

import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.service.message.domain.chat.service.ImChatService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DomainServiceBeansTest {

    @Test
    void imChatServiceBeanOnlyDependsOnMessageOwnedRepositories() throws NoSuchMethodException {
        Method method = DomainServiceBeans.class.getMethod(
                "imChatService",
                SnowflakeId.class,
                ImPrivateChatRepository.class,
                ImGroupChatRepository.class);

        assertThat(method.getReturnType()).isEqualTo(ImChatService.class);
    }
}
