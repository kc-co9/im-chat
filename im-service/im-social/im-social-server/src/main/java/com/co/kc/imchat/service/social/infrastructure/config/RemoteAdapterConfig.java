package com.co.kc.imchat.service.social.infrastructure.config;

import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.message.facade.ChatService;
import com.co.kc.imchat.service.social.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.social.adapter.message.MessageSocialAdapter;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "im.social.provider", name = "enabled", havingValue = "true")
public class RemoteAdapterConfig {

    @DubboReference(interfaceClass = AccountService.class, version = "1.0.0")
    private AccountService accountService;

    @DubboReference(interfaceClass = ChatService.class, version = "1.0.0")
    private ChatService chatService;

    @Bean
    @ConditionalOnMissingBean
    public AccountAdapter accountAdapter() {
        return new AccountAdapter(accountService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageSocialAdapter messageSocialAdapter() {
        return new MessageSocialAdapter(chatService);
    }
}
