package com.co.kc.imchat.interfaces.model.io.friend;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class FriendUnblockRequest {
    @NotNull(message = "好友不能为空")
    private Long friendUserId;
}
