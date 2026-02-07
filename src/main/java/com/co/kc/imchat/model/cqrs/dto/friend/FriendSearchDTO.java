package com.co.kc.imchat.model.cqrs.dto.friend;

import lombok.Data;

@Data
public class FriendSearchDTO {
    private Long userId;
    private String username;
}
