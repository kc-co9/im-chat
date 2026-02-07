package com.co.kc.imchat.model.io.friend;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class FriendAddRequest {
    @NotNull(message = "好友不能为空")
    private Long friendUserId;
}
