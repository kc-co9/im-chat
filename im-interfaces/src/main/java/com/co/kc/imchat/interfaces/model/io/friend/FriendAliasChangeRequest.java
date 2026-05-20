package com.co.kc.imchat.interfaces.model.io.friend;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class FriendAliasChangeRequest {
    @NotNull(message = "好友不能为空")
    private Long friendUserId;

    @NotBlank(message = "好友备注不能为空")
    private String friendAlias;
}
