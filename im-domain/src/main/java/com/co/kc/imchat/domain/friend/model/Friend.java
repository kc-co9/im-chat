package com.co.kc.imchat.domain.friend.model;

import com.co.kc.imchat.domain.shared.model.Identification;
import com.co.kc.imchat.domain.shared.model.Validator;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
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
    private UserName friendName;
    private FriendAlias friendAlias;
    private FriendStatus status;
    private LocalDateTime createTime;

    public void block() {
        status = FriendStatus.BLOCKED;
    }

    public void unblock() {
        status = FriendStatus.NORMAL;
    }

    public boolean isNormal() {
        return status == FriendStatus.NORMAL;
    }

    public boolean isBlocked() {
        return status == FriendStatus.BLOCKED;
    }

    public void changeAlias(FriendAlias alias) {
        friendAlias = alias;
    }

    public FriendDisplayName displayName() {
        if (friendAlias != null) {
            return new FriendDisplayName(friendAlias.getValue());
        } else {
            return new FriendDisplayName(friendName.getValue());
        }
    }

}
