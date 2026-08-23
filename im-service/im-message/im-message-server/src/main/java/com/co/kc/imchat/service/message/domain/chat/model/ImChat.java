package com.co.kc.imchat.service.message.domain.chat.model;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.domain.shared.model.Validator;
import com.co.kc.imchat.common.domain.user.model.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 聚合根：聊天会话。
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

    public boolean belongsTo(UserId userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    @Override
    public void validate() {
        if (id == null || type == null || userId == null) {
            throw new IllegalStateException("聊天缺少 id、type 或 userId");
        }
    }
}
