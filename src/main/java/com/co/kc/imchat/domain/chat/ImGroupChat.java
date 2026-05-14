package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.group.GroupAlias;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupChat extends ImChat {
    private GroupId groupId;
    private GroupAlias groupAlias;
    private ImMessageId lastMessageId;
    private ImMessageId readMessageId;
    private LocalDateTime readTime;
    private Integer unreadMessageCount;

    public boolean contain(UserId userId) {
        return getUserId() != null && getUserId().equals(userId);
    }

    public void receiveLatestMessage(ImGroupInboxMessage message, boolean isChatting) {
        activate(message.getSendTime());
        this.lastMessageId = message.getId();
        if (isChatting) {
            readMessage(message);
        } else {
            this.unreadMessageCount++;
        }
    }

    public void readMessage(ImGroupInboxMessage message) {
        this.readMessageId = message.getId();
        this.readTime = LocalDateTime.now();
        this.unreadMessageCount = 0;
        message.read(getUserId());
    }

    public void readToLatest() {
        if (lastMessageId != null) {
            this.readMessageId = lastMessageId;
            this.readTime = LocalDateTime.now();
        }
        this.unreadMessageCount = 0;
    }

    @Override
    public void validate() {
        super.validate();
        if (groupId == null || unreadMessageCount == null) {
            throw new IllegalStateException("群聊会话缺少 groupId 或 unreadMessageCount");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImGroupChat chat = new ImGroupChat();

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

        public Builder groupId(GroupId groupId) {
            chat.setGroupId(groupId);
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

        public Builder groupAlias(GroupAlias groupAlias) {
            chat.setGroupAlias(groupAlias);
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

        public Builder readTime(LocalDateTime readTime) {
            chat.setReadTime(readTime);
            return this;
        }

        public Builder unreadMessageCount(Integer unreadMessageCount) {
            chat.setUnreadMessageCount(unreadMessageCount);
            return this;
        }

        public ImGroupChat build() {
            if (chat.getStatus() == null) {
                chat.setStatus(ImChatStatus.NORMAL);
            }
            chat.validate();
            return chat;
        }
    }
}
