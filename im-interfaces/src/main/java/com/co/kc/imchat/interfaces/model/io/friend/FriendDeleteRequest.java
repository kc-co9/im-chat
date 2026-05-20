package com.co.kc.imchat.interfaces.model.io.friend;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class FriendDeleteRequest {
    @NotNull(message = "好友不能为空")
    private Long friendUserId;
}
