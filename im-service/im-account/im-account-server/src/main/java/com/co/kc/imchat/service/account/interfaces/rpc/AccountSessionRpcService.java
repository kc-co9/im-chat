package com.co.kc.imchat.service.account.interfaces.rpc;

import com.co.kc.imchat.service.account.application.AccountSessionAppService;
import com.co.kc.imchat.service.account.facade.AccountSessionService;
import com.co.kc.imchat.service.account.facade.dto.UserChattingCheckDTO;
import com.co.kc.imchat.service.account.facade.dto.UserOnlineCheckDTO;
import com.co.kc.imchat.service.account.facade.params.ChatEnterParams;
import com.co.kc.imchat.service.account.facade.params.ChatExitParams;
import com.co.kc.imchat.service.account.facade.params.UserChattingCheckParams;
import com.co.kc.imchat.service.account.facade.params.UserOnlineCheckParams;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 账号会话 RPC 服务。
 */
@Component
@RequiredArgsConstructor
@DubboService(interfaceClass = AccountSessionService.class, version = "1.0.0")
@ConditionalOnProperty(prefix = "im.account.provider", name = "enabled", havingValue = "true")
public class AccountSessionRpcService implements AccountSessionService {
    private final AccountSessionAppService accountSessionAppService;

    @Override
    public void enterChat(ChatEnterParams params) {
        accountSessionAppService.enterChat(params);
    }

    @Override
    public void exitChat(ChatExitParams params) {
        accountSessionAppService.exitChat(params);
    }

    @Override
    public UserOnlineCheckDTO checkUserOnline(UserOnlineCheckParams params) {
        return accountSessionAppService.checkUserOnline(params);
    }

    @Override
    public UserChattingCheckDTO checkUserChatting(UserChattingCheckParams params) {
        return accountSessionAppService.checkUserChatting(params);
    }
}
