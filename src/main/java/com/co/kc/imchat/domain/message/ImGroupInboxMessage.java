package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupInboxMessage extends ImMessage {
    private ImGroupId groupId;
    private ImChatId chatId;
    private UserId userId;
    private ImGroupMessageStatus status;
    private LocalDateTime receivedTime;
    private LocalDateTime readTime;

    public void receive(UserId userId) {
        if (!this.userId.equals(userId) || this.senderId.equals(userId)) {
            throw new IllegalArgumentException("用户不能接收别人的群消息");
        }
        this.status = this.status.transition(ImMessageEvent.RECEIVE);
        this.receivedTime = LocalDateTime.now();
    }

    public void read(UserId userId) {
        if (!this.userId.equals(userId)) {
            throw new IllegalArgumentException("用户不能读取别人的群消息");
        }
        this.status = this.status.transition(ImMessageEvent.READ);
        this.readTime = LocalDateTime.now();
    }

    public void revoke(UserId senderId) {
        if (!this.senderId.equals(senderId)) {
            throw new IllegalArgumentException("用户不能撤回别人的群消息");
        }
        this.status = this.status.transition(ImMessageEvent.REVOKE);
        this.revokeTime = LocalDateTime.now();
    }

    @Override
    public void validate() {
        super.validate();
        if (groupId == null || chatId == null || userId == null || status == null) {
            throw new IllegalStateException("群聊收件箱消息缺少 groupId、chatId、userId 或 status");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImGroupInboxMessage message = new ImGroupInboxMessage();

        public Builder pkId(Long pkId) {
            if (pkId != null) {
                message.setPkId(pkId);
            }
            return this;
        }

        public Builder id(ImMessageId id) {
            message.setId(id);
            return this;
        }

        public Builder token(ImMessageToken token) {
            message.setToken(token);
            return this;
        }

        public Builder content(ImMessageContent content) {
            message.setContent(content);
            return this;
        }

        public Builder groupId(ImGroupId groupId) {
            message.setGroupId(groupId);
            return this;
        }

        public Builder chatId(ImChatId chatId) {
            message.setChatId(chatId);
            return this;
        }

        public Builder userId(UserId userId) {
            message.setUserId(userId);
            return this;
        }

        public Builder senderId(UserId senderId) {
            message.setSenderId(senderId);
            return this;
        }

        public Builder status(ImGroupMessageStatus status) {
            message.setStatus(status);
            return this;
        }

        public Builder sendTime(LocalDateTime sendTime) {
            message.setSendTime(sendTime);
            return this;
        }

        public Builder receivedTime(LocalDateTime receivedTime) {
            message.setReceivedTime(receivedTime);
            return this;
        }

        public Builder readTime(LocalDateTime readTime) {
            message.setReadTime(readTime);
            return this;
        }

        public Builder revokeTime(LocalDateTime revokeTime) {
            message.setRevokeTime(revokeTime);
            return this;
        }

        public ImGroupInboxMessage build() {
            message.validate();
            return message;
        }
    }
}
