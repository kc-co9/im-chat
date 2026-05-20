package com.co.kc.imchat.interfaces.model.io.group;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class GroupAliasChangeRequest {
    @NotNull(message = "群聊不能为空")
    private Long chatId;

    @NotBlank(message = "群备注不能为空")
    private String groupAlias;
}
