package com.co.kc.imchat.model.io.group;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class GroupMemberAliasChangeRequest {
    @NotNull(message = "群聊不能为空")
    private Long groupId;

    @NotBlank(message = "群内昵称不能为空")
    private String userAlias;
}
