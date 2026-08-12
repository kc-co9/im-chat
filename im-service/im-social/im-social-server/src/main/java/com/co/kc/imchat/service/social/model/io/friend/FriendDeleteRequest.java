package com.co.kc.imchat.service.social.model.io.friend;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class FriendDeleteRequest {
    @NotNull(message = "好友不能为空")
    private Long friendUserId;
}
