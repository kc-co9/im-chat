package com.co.kc.imchat.service.account.interfaces.rpc;

import com.co.kc.imchat.service.account.application.SessionAppService;
import com.co.kc.imchat.service.account.application.UserAppService;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;
import com.co.kc.imchat.service.account.facade.params.UserProfilesGetParams;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@DubboService(interfaceClass = AccountService.class, version = "1.0.0")
public class AccountRpcService implements AccountService {
    private final SessionAppService sessionAppService;
    private final UserAppService userAppService;

    public AccountRpcService(SessionAppService sessionAppService, UserAppService userAppService) {
        this.sessionAppService = sessionAppService;
        this.userAppService = userAppService;
    }

    @Override
    public SessionAuthDTO authenticate(AccessTokenParams params) {
        return sessionAppService.authenticate(params);
    }

    @Override
    public UserProfileDTO getUserProfile(UserProfileGetParams params) {
        return userAppService.getUserProfile(params);
    }

    @Override
    public List<UserProfileDTO> getUserProfiles(UserProfilesGetParams params) {
        return userAppService.getUserProfiles(params);
    }

    @Override
    public UserProfileFindDTO findUserProfileByEmail(UserProfileFindParams params) {
        return userAppService.findUserProfileByEmail(params);
    }
}
