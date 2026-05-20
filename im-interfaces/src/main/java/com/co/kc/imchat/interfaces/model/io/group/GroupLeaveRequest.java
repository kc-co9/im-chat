package com.co.kc.imchat.interfaces.model.io.group;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class GroupLeaveRequest {
    @NotNull(message = "群聊不能为空")
    private Long groupId;
}
