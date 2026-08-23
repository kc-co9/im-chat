package com.co.kc.imchat.service.message.domain.chat.model;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.common.domain.user.model.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 聚合根：私聊会话（每人一条持久化记录：{@link #userId} 为记录归属方，{@link #peerUserId} 为对端）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateChat extends ImChat {
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
        if (userId == null || getUserId() == null || peerUserId == null) {
            return false;
        }
        return userId.equals(getUserId()) || userId.equals(peerUserId);
    }

    public void receiveLatestMessage(ImPrivateInboxMessage message, boolean isChatting) {
        activate(message.getSendTime());
        this.lastMessageId = message.getId();
        if (isChatting) {
            readMessage(message);
        } else {
            this.unreadMessageCount = (this.unreadMessageCount == null ? 0 : this.unreadMessageCount) + 1;
        }
    }

    public void readMessage(ImPrivateInboxMessage message) {
        this.readMessageId = message.getId();
        this.unreadMessageCount = 0;
    }

    /** 将当前私聊会话标记为已读到最新消息。 */
    public void readToLatest() {
        this.readMessageId = lastMessageId;
        this.unreadMessageCount = 0;
    }

    @Override
    public void validate() {
        super.validate();
        if (peerUserId == null) {
            throw new IllegalStateException("私聊会话缺少 peerUserId");
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

        public Builder status(ImChatStatus status) {
            chat.setStatus(status);
            return this;
        }

        public Builder activeTime(LocalDateTime activeTime) {
            chat.setActiveTime(activeTime);
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
            if (chat.getStatus() == null) {
                chat.setStatus(ImChatStatus.NORMAL);
            }
            if (chat.getUnreadMessageCount() == null) {
                chat.setUnreadMessageCount(0);
            }
            chat.validate();
            return chat;
        }
    }
}
