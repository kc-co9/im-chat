package com.co.kc.imchat.service.social.model.io.group;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class GroupMemberAliasChangeRequest {
    @NotNull(message = "群聊不能为空")
    private Long groupId;

    @NotBlank(message = "群内昵称不能为空")
    private String userAlias;
}
