package com.co.kc.imchat.model.io.friend;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class FriendAliasChangeRequest {
    @NotNull(message = "好友不能为空")
    private Long friendUserId;

    @NotBlank(message = "好友备注不能为空")
    private String friendAlias;
}
