package com.co.kc.imchat.service.social.model.io.group;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class GroupTransferOwnerRequest {
    @NotNull(message = "群聊不能为空")
    private Long groupId;

    @NotNull(message = "新群主不能为空")
    private Long newOwnerId;
}
