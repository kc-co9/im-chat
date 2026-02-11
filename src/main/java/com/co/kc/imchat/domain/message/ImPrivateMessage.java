package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IM消息领域模型
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateMessage extends ImMessage {
    private UserId receiverId;
    private ImPrivateMessageStatus status;
    private LocalDateTime receivedTime;
    private LocalDateTime readTime;

    public void receive(UserId receiverId) {
        if (!this.receiverId.equals(receiverId)) {
            throw new IllegalArgumentException("用户不能接收别人的消息");
        }
        this.status = this.status.transition(ImMessageEvent.RECEIVE);
        this.receivedTime = LocalDateTime.now();
    }

    public void read(UserId receiverId) {
        if (!this.receiverId.equals(receiverId)) {
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


}
