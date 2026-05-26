package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.message.event.ImMessageEvent;
import com.co.kc.imchat.domain.user.model.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 聚合根：私聊收件箱消息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateInboxMessage extends ImMessage {
    private ImChatId chatId;
    private UserId userId;
    private ImPrivateMessageStatus status;
    private LocalDateTime receivedTime;
    private LocalDateTime readTime;

    public void receive(UserId userId) {
        if (!this.userId.equals(userId) || this.senderId.equals(userId)) {
            throw new IllegalArgumentException("用户不能接收别人的消息");
        }
        this.status = this.status.transition(ImMessageEvent.RECEIVE);
        this.receivedTime = LocalDateTime.now();
    }

    public void read(UserId userId) {
        if (!this.userId.equals(userId) || this.senderId.equals(userId)) {
            throw new IllegalArgumentException("用户不能接收别人的消息");
        }
        this.status = this.status.transition(ImMessageEvent.READ);
        this.readTime = LocalDateTime.now();
    }

    public void revoke(UserId senderId) {
        if (!this.senderId.equals(senderId)) {
            throw new IllegalArgumentException("用户不能撤回别人的消息");
        }
        this.status = this.status.transition(ImMessageEvent.REVOKE);
        this.revokeTime = LocalDateTime.now();
    }

    @Override
    public String getVisibleContent() {
        if (status == ImPrivateMessageStatus.REVOKED) {
            return null;
        }
        return super.getVisibleContent();
    }

    @Override
    public void validate() {
        super.validate();
        if (chatId == null || userId == null || status == null) {
            throw new IllegalStateException("私聊收件箱消息缺少 chatId、userId 或 status");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ImPrivateInboxMessage message = new ImPrivateInboxMessage();

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

        public Builder status(ImPrivateMessageStatus status) {
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

        public ImPrivateInboxMessage build() {
            message.validate();
            return message;
        }
    }
}
