package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import com.co.kc.imchat.domain.user.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * IM群成员
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupMember extends Identification implements Validator {
    private ImGroupMemberId id;
    private UserId userId;
    private ImChatId chatId;
    private ImGroupAlias groupAlias;
    private ImGroupUserAlias userAlias;
}
