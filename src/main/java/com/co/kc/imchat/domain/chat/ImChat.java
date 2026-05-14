package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 聊天-领域模型
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ImChat extends Identification implements Validator {
    private ImChatId id;
    private ImChatType type;
    private UserId userId;
    private ImChatStatus status;
    private LocalDateTime activeTime;

    public void hide() {
        this.status = ImChatStatus.HIDDEN;
    }

    public void activate(LocalDateTime activeTime) {
        this.status = ImChatStatus.NORMAL;
        this.activeTime = activeTime == null ? LocalDateTime.now() : activeTime;
    }

    public boolean isVisible() {
        return status == ImChatStatus.NORMAL;
    }

    @Override
    public void validate() {
        if (id == null || type == null || userId == null) {
            throw new IllegalStateException("聊天缺少 id、type 或 userId");
        }
    }
}
