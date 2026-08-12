package com.co.kc.imchat.service.message.adapter.account;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.service.message.domain.account.model.AuthenticatedUser;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.AccountSessionService;
import com.co.kc.imchat.service.account.facade.dto.TokenValidateDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.params.ChatEnterParams;
import com.co.kc.imchat.service.account.facade.params.ChatExitParams;
import com.co.kc.imchat.service.account.facade.params.TokenValidateParams;
import com.co.kc.imchat.service.account.facade.params.UserChattingCheckParams;
import com.co.kc.imchat.service.account.facade.params.UserOnlineCheckParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;

import java.util.Objects;

/**
 * 消息服务访问账号服务的适配器。
 */
public class AccountAdapter {

    private final AccountService accountService;
    private final AccountSessionService accountSessionService;

    public AccountAdapter(AccountService accountService, AccountSessionService accountSessionService) {
        this.accountService = Objects.requireNonNull(accountService, "accountService");
        this.accountSessionService = Objects.requireNonNull(accountSessionService, "accountSessionService");
    }

    /**
     * 根据登录令牌解析已认证用户。
     *
     * @param token 登录令牌
     * @return 已认证用户信息
     */
    public AuthenticatedUser authenticate(String token) {
        TokenValidateDTO tokenResponse = accountService.validateToken(new TokenValidateParams(token));
        if (!tokenResponse.valid() || tokenResponse.userId() == null) {
            throw new AuthException("用户未登录");
        }
        UserProfileDTO profile = accountService.getUserProfile(new UserProfileGetParams(tokenResponse.userId()));
        return new AuthenticatedUser(profile.userId(), profile.email(), profile.username());
    }

    /**
     * 标记用户进入指定聊天会话。
     *
     * @param userId 用户 ID
     * @param chatId 会话 ID
     */
    public void enterChat(Long userId, Long chatId) {
        accountSessionService.enterChat(new ChatEnterParams(userId, chatId));
    }

    /**
     * 标记用户退出当前聊天会话。
     *
     * @param userId 用户 ID
     */
    public void exitChat(Long userId) {
        accountSessionService.exitChat(new ChatExitParams(userId));
    }

    /**
     * 判断用户是否在线。
     *
     * @param userId 用户 ID
     * @return 是否在线
     */
    public boolean isOnline(Long userId) {
        return accountSessionService.checkUserOnline(new UserOnlineCheckParams(userId)).online();
    }

    /**
     * 判断用户是否正在指定会话中。
     *
     * @param userId 用户 ID
     * @param chatId 会话 ID
     * @return 是否正在会话中
     */
    public boolean isChatting(Long userId, Long chatId) {
        return accountSessionService.checkUserChatting(new UserChattingCheckParams(userId, chatId)).chatting();
    }
}
