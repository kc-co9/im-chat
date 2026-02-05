package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChatId;
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
    private ImChatId chatId;
    protected UserId senderId;
    protected ImMessageStatus status;
    protected LocalDateTime sendTime;
    protected LocalDateTime revokeTime;
}
