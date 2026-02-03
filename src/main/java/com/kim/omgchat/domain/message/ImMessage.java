package com.kim.omgchat.domain.message;

import com.kim.omgchat.domain.shared.Identification;
import com.kim.omgchat.domain.shared.Validator;
import com.kim.omgchat.domain.user.UserId;
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
    protected ImMessageStatus status;
    protected LocalDateTime sendTime;
    protected LocalDateTime readTime;
    protected LocalDateTime revokeTime;
}
