package com.kim.omgchat.domain.chat;

import com.kim.omgchat.domain.shared.NickName;
import com.kim.omgchat.domain.user.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * IM群成员
 */
@Data
@AllArgsConstructor
public class ImGroupMember {
    private UserId userId;
    private NickName nickName;
}
