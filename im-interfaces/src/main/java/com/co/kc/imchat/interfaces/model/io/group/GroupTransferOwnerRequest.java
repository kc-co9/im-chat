package com.co.kc.imchat.interfaces.model.io.group;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class GroupTransferOwnerRequest {
    @NotNull(message = "群聊不能为空")
    private Long groupId;

    @NotNull(message = "新群主不能为空")
    private Long newOwnerId;
}
