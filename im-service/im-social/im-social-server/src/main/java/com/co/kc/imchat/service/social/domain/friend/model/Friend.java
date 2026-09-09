package com.co.kc.imchat.service.social.domain.friend.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.domain.shared.model.Validator;
import com.co.kc.imchat.common.domain.user.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 聚合根：好友关系。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Friend extends Identification implements Validator, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private FriendId id;
    private UserId userId;
    private UserId friendUserId;
    private FriendAlias friendAlias;
    private FriendStatus status;
    private Instant createTime;

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

}
