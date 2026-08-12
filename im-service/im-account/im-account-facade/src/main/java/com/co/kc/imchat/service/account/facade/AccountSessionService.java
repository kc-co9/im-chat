package com.co.kc.imchat.service.account.facade;

import com.co.kc.imchat.service.account.facade.dto.UserChattingCheckDTO;
import com.co.kc.imchat.service.account.facade.dto.UserOnlineCheckDTO;
import com.co.kc.imchat.service.account.facade.params.ChatEnterParams;
import com.co.kc.imchat.service.account.facade.params.ChatExitParams;
import com.co.kc.imchat.service.account.facade.params.UserChattingCheckParams;
import com.co.kc.imchat.service.account.facade.params.UserOnlineCheckParams;

/**
 * 账号会话服务契约。
 * <p>
 * 提供用户在线状态和当前聊天会话状态能力。
 */
public interface AccountSessionService {
    /**
     * 标记用户进入指定聊天会话。
     *
     * @param params 进入会话请求
     */
    void enterChat(ChatEnterParams params);

    /**
     * 标记用户退出当前聊天会话。
     *
     * @param params 退出会话请求
     */
    void exitChat(ChatExitParams params);

    /**
     * 查询用户是否在线。
     *
     * @param params 在线状态查询请求
     * @return 在线状态
     */
    UserOnlineCheckDTO checkUserOnline(UserOnlineCheckParams params);

    /**
     * 查询用户是否正在指定会话内。
     *
     * @param params 会话状态查询请求
     * @return 会话状态
     */
    UserChattingCheckDTO checkUserChatting(UserChattingCheckParams params);
}
