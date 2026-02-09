package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupChat extends ImChat {
    private UserId ownerId;
    private ImGroupName name;
    private ImGroupNotification notification;
    private ImGroupSetting setting;
}
