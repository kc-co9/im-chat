package com.co.kc.imchat.model.io.group;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class GroupNotificationChangeRequest {
    @NotNull(message = "群聊不能为空")
    private Long groupId;

    private String notification;
}
