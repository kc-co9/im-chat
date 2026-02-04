package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.shared.NickName;
import com.co.kc.imchat.domain.user.UserId;
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
