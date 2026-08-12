package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.model.SessionStatus;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.facade.dto.UserChattingCheckDTO;
import com.co.kc.imchat.service.account.facade.dto.UserOnlineCheckDTO;
import com.co.kc.imchat.service.account.facade.params.ChatEnterParams;
import com.co.kc.imchat.service.account.facade.params.ChatExitParams;
import com.co.kc.imchat.service.account.facade.params.UserChattingCheckParams;
import com.co.kc.imchat.service.account.facade.params.UserOnlineCheckParams;
import com.co.kc.imchat.service.account.transformer.AccountAppTransformer;
import lombok.RequiredArgsConstructor;

/**
 * 账号会话应用服务，处理在线状态和当前聊天会话状态。
 */
@RequiredArgsConstructor
public class AccountSessionAppService {
    private final SessionRepository sessionRepository;

    public void enterChat(ChatEnterParams params) {
        Session session = findSignedInSession(params.userId());
        session.onEnterChat(params.chatId());
        sessionRepository.save(session);
    }

    public void exitChat(ChatExitParams params) {
        Session session = findSignedInSession(params.userId());
        session.onExitChat();
        sessionRepository.save(session);
    }

    public UserOnlineCheckDTO checkUserOnline(UserOnlineCheckParams params) {
        return AccountAppTransformer.INSTANCE.userOnlineCheckDtoFrom(isOnline(new UserId(params.userId())));
    }

    public UserChattingCheckDTO checkUserChatting(UserChattingCheckParams params) {
        boolean chatting = sessionRepository.find(new UserId(params.userId()))
                .map(session -> SessionStatus.ONLINE.equals(session.getStatus())
                        && params.chatId().equals(session.getChatId()))
                .orElse(false);
        return AccountAppTransformer.INSTANCE.userChattingCheckDtoFrom(chatting);
    }

    private boolean isOnline(UserId userId) {
        return sessionRepository.find(userId)
                .map(session -> SessionStatus.ONLINE.equals(session.getStatus()))
                .orElse(false);
    }

    private Session findSignedInSession(Long userId) {
        Session session = sessionRepository.find(new UserId(userId))
                .orElseThrow(() -> new AuthException("用户尚未登陆"));
        if (!session.isSignIn()) {
            throw new AuthException("用户尚未登陆");
        }
        return session;
    }
}
