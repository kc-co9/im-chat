package com.kim.omgchat.domain.chat;

import com.kim.omgchat.domain.user.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImPrivatePair {
    private UserId member1;
    private UserId member2;
}
