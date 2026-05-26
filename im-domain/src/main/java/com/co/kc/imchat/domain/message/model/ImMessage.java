package com.co.kc.imchat.domain.message.model;

import com.co.kc.imchat.domain.shared.model.Identification;
import com.co.kc.imchat.domain.shared.model.Validator;
import com.co.kc.imchat.domain.user.model.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 聚合根：消息。
 */
@EqualsAndHashCode(callSuper = false)
@Data
public class ImMessage extends Identification implements Validator {
    protected ImMessageId id;
    protected ImMessageToken token;
    protected ImMessageContent content;
    protected UserId senderId;
    protected LocalDateTime sendTime;
    protected LocalDateTime revokeTime;

    public String getVisibleContent() {
        return content.value();
    }

    @Override
    public void validate() {
        if (id == null || token == null || content == null || senderId == null || sendTime == null) {
            throw new IllegalStateException("消息缺少 id、token、content、senderId 或 sendTime");
        }
        if (content.type() == null) {
            throw new IllegalStateException("消息 content.type 不能为空");
        }
    }
}
