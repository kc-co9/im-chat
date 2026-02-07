package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import com.co.kc.imchat.domain.user.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 朋友
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Friend extends Identification implements Validator {
    private FriendId id;
    private UserId userId;
    private UserId friendUserId;
    private FriendAlias alias;
    private FriendStatus status;
    private LocalDateTime createTime;

    public void block() {
        status = FriendStatus.BLOCKED;
    }

    public void unblock() {
        status = FriendStatus.NORMAL;
    }
}
