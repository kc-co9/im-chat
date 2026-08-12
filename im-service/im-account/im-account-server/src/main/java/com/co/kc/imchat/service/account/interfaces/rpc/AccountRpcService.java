package com.co.kc.imchat.service.account.interfaces.rpc;

import com.co.kc.imchat.service.account.application.AccountAppService;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.TokenValidateDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.TokenValidateParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@DubboService(interfaceClass = AccountService.class, version = "1.0.0")
@ConditionalOnProperty(prefix = "im.account.provider", name = "enabled", havingValue = "true")
public class AccountRpcService implements AccountService {

    private final AccountAppService accountAppService;
    private final UserAppService userAppService;

    public AccountRpcService(AccountAppService accountAppService, UserAppService userAppService) {
        this.accountAppService = accountAppService;
        this.userAppService = userAppService;
    }

    @Override
    public TokenValidateDTO validateToken(TokenValidateParams params) {
        return accountAppService.validateToken(params);
    }

    @Override
    public UserProfileDTO getUserProfile(UserProfileGetParams params) {
        return userAppService.getUserProfile(params);
    }

    @Override
    public UserProfileFindDTO findUserProfileByEmail(UserProfileFindParams params) {
        return userAppService.findUserProfileByEmail(params);
    }
}
