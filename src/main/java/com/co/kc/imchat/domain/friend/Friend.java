package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.user.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class Friend {
    private UserId userId;
    private UserId friendId;
    private FriendAlias alias;
    private FriendStatus status;
    private LocalDateTime createTime;

    public void block(){
        status = FriendStatus.BLOCKED;
    }

    public void unblock() {
        status = FriendStatus.NORMAL;
    }
}
