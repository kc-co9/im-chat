package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = false)
@Data
public class ImMessage extends Identification implements Validator {
    protected ImMessageId id;
    protected ImMessageToken token;
    protected ImMessageContent content;
    protected UserId senderId;
    protected LocalDateTime sendTime;
    protected LocalDateTime revokeTime;

    @Override
    public void validate() {
        if (id == null || token == null || content == null || senderId == null || sendTime == null) {
            throw new IllegalStateException("消息缺少 id、token、content、senderId 或 sendTime");
        }
        if (content.getType() == null) {
            throw new IllegalStateException("消息 content.type 不能为空");
        }
    }
}
