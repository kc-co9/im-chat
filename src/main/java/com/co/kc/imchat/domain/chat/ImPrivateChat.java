package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImPrivateInboxMessage;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 私聊-领域模型（每人一条持久化记录：{@link #userId} 为记录归属方，{@link #peerUserId} 为对端）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateChat extends ImChat {
    /**
     * 本条记录所属用户（客户端使用的 chatId 对应 {@link ImChat#getId()}）。
     */
    private UserId userId;
    /**
     * 会话对端用户。
     */
    private UserId peerUserId;
    /**
     * 当前用户视角下的最新消息。
     */
    private ImMessageId lastMessageId;
    /**
     * 当前用户视角下已读到的消息。
     */
    private ImMessageId readMessageId;
    /**
     * 当前用户视角下未读消息数。
     */
    private Integer unreadMessageCount;

    public UserId getAnother(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        if (userId.equals(this.userId)) {
            return peerUserId;
        }
        if (userId.equals(peerUserId)) {
            return this.userId;
        }
        throw new IllegalArgumentException("用户不在会话中");
    }

    public boolean contain(UserId userId) {
        if (userId == null || this.userId == null || peerUserId == null) {
            return false;
        }
        return userId.equals(this.userId) || userId.equals(peerUserId);
    }

    public void receiveLatestMessage(ImPrivateInboxMessage message, boolean isChatting) {
        this.lastMessageId = message.getId();
        if (isChatting) {
            readMessage(message);
        } else {
            this.unreadMessageCount++;
        }
    }

    public void readMessage(ImPrivateInboxMessage message) {
        this.readMessageId = message.getId();
        this.unreadMessageCount = 0;
    }

}
