package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class ImPrivatePair {
    private UserId member1;
    private UserId member2;

    public ImPrivatePair(UserId userId1, UserId userId2) {
        if (userId1 == null || userId2 == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        if (userId1.equals(userId2)) {
            throw new IllegalArgumentException("用户不能是同一个");
        }
        if (userId1.getValue() < userId2.getValue()) {
            member1 = userId1;
            member2 = userId2;
        } else {
            member1 = userId2;
            member2 = userId1;
        }
    }

    public boolean contain(UserId userId) {
        return member1.equals(userId) || member2.equals(userId);
    }
}
