package com.co.kc.imchat.service.message.infrastructure.config;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.AccountSessionService;
import com.co.kc.imchat.service.message.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import com.co.kc.imchat.service.social.facade.SocialService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "im.message.remote-adapter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RemoteAdapterConfig {

    @DubboReference(interfaceClass = AccountService.class, version = "1.0.0")
    private AccountService accountService;

    @DubboReference(interfaceClass = AccountSessionService.class, version = "1.0.0")
    private AccountSessionService accountSessionService;

    @DubboReference(interfaceClass = SocialService.class, version = "1.0.0")
    private SocialService socialService;

    @Bean
    @ConditionalOnMissingBean
    public BrokerClient brokerClient(BoltInvoker boltInvoker,
                                     @Value("${im.message.broker.bolt.address:127.0.0.1:12200}")
                                     String brokerAddress,
                                     @Value("${im.message.broker.bolt.timeout-millis:3000}")
                                     int timeoutMillis) {
        return new BrokerClient(boltInvoker, brokerAddress, timeoutMillis);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountAdapter accountAdapter() {
        return new AccountAdapter(accountService, accountSessionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public SocialAdapter socialAdapter() {
        return new SocialAdapter(socialService);
    }
}
