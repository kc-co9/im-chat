package com.co.kc.imchat.interfaces.model.io.friend;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendDetailResponse {
    private Long userId;
    private String username;
    private String alias;
    private LocalDateTime createTime;
}
