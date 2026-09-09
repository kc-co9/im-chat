package com.co.kc.imchat.management.admin.infrastructure.config.beans;

import com.co.kc.imchat.management.admin.adapter.AccountAdminAdapter;
import com.co.kc.imchat.management.admin.application.ManagedUserAppService;
import com.co.kc.imchat.service.account.admin.facade.AccountAdminService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdapterBeans {
    @DubboReference(interfaceClass = AccountAdminService.class, version = "1.0.0", retries = 0)
    private AccountAdminService accountAdminService;

    @Bean
    public AccountAdminAdapter accountAdminAdapter() {
        return new AccountAdminAdapter(accountAdminService);
    }

    @Bean
    public ManagedUserAppService managedUserAppService(AccountAdminAdapter accountAdminAdapter) {
        return new ManagedUserAppService(accountAdminAdapter);
    }
}
