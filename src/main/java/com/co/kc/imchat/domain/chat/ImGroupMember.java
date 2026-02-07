package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IM群成员
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupMember {
    private UserId userId;
    private ImGroupAlias groupAlias;
    private ImGroupUserAlias userAlias;
    private ImGroupMemberSetting setting;
}
