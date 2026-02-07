package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupChat extends ImChat {
    private UserId ownerId;
    private List<ImGroupMember> members;
    private ImGroupNotification notification;
    private ImGroupSetting setting;

    public boolean contain(UserId userId) {
        return members.stream().anyMatch(member -> member.getUserId().equals(userId));
    }
}
