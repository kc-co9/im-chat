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

    @Override
    public void validate() {
        super.validate();
        if (userId == null || peerUserId == null) {
            throw new IllegalStateException("私聊会话缺少 userId 或 peerUserId");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImPrivateChat chat = new ImPrivateChat();

        public Builder pkId(Long pkId) {
            if (pkId != null) {
                chat.setPkId(pkId);
            }
            return this;
        }

        public Builder id(ImChatId id) {
            chat.setId(id);
            return this;
        }

        public Builder type(ImChatType type) {
            chat.setType(type);
            return this;
        }

        public Builder userId(UserId userId) {
            chat.setUserId(userId);
            return this;
        }

        public Builder peerUserId(UserId peerUserId) {
            chat.setPeerUserId(peerUserId);
            return this;
        }

        public Builder lastMessageId(ImMessageId lastMessageId) {
            chat.setLastMessageId(lastMessageId);
            return this;
        }

        public Builder readMessageId(ImMessageId readMessageId) {
            chat.setReadMessageId(readMessageId);
            return this;
        }

        public Builder unreadMessageCount(Integer unreadMessageCount) {
            chat.setUnreadMessageCount(unreadMessageCount);
            return this;
        }

        public ImPrivateChat build() {
            chat.validate();
            return chat;
        }
    }
}
