package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.group.ImGroupAlias;
import com.co.kc.imchat.domain.group.ImGroupId;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupChat extends ImChat {
    private ImGroupId groupId;
    private ImGroupAlias groupAlias;
    private ImMessageId lastMessageId;
    private ImMessageId readMessageId;
    private Integer unreadMessageCount;

    public boolean contain(UserId userId) {
        return getUserId() != null && getUserId().equals(userId);
    }

    public void receiveLatestMessage(ImGroupInboxMessage message, boolean isChatting) {
        this.lastMessageId = message.getId();
        if (isChatting) {
            readMessage(message);
        } else {
            this.unreadMessageCount++;
        }
    }

    public void readMessage(ImGroupInboxMessage message) {
        this.readMessageId = message.getId();
        this.unreadMessageCount = 0;
        message.read(getUserId());
    }

    public void readToLatest() {
        if (lastMessageId != null) {
            this.readMessageId = lastMessageId;
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

        public Builder groupId(ImGroupId groupId) {
            chat.setGroupId(groupId);
            return this;
        }

        public Builder userId(UserId userId) {
            chat.setUserId(userId);
            return this;
        }

        public Builder groupAlias(ImGroupAlias groupAlias) {
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

        public Builder unreadMessageCount(Integer unreadMessageCount) {
            chat.setUnreadMessageCount(unreadMessageCount);
            return this;
        }

        public ImGroupChat build() {
            chat.validate();
            return chat;
        }
    }
}
