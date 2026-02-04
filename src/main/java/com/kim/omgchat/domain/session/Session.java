package com.kim.omgchat.domain.session;

import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.user.UserId;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Session {
    private final UserId userId;

    private ImChatId chatId;
    private SessionStatus status;
    private LocalDateTime signInTime;
    private LocalDateTime signOutTime;

    public Session(UserId userId) {
        this.userId = userId;
    }

    public boolean isSignIn() {
        return this.status == SessionStatus.ONLINE;
    }

    public void onSignIn() {
        this.status = SessionStatus.ONLINE;
        this.chatId = null;
        this.signInTime = LocalDateTime.now();
        this.signOutTime = null;
    }

    public void onSignOut() {
        this.status = SessionStatus.OFFLINE;
        this.chatId = null;
        this.signInTime = null;
        this.signOutTime = LocalDateTime.now();
    }

    public void onEnterChat(ImChatId chatId) {
        this.chatId = chatId;
    }

    public void onExitChat() {
        this.chatId = null;
    }


}
