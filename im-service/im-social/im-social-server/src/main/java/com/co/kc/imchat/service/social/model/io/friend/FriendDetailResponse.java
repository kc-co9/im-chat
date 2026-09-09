package com.co.kc.imchat.service.social.model.io.friend;

import lombok.Data;

@Data
public class FriendDetailResponse {
    private Long userId;
    private String username;
    private String alias;
    private Long createTime;
}
