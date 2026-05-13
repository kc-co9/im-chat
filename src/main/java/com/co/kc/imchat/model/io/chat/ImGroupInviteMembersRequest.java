package com.co.kc.imchat.model.io.chat;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class ImGroupInviteMembersRequest {
    @NotNull(message = "群聊不能为空")
    private Long groupId;
    @NotEmpty(message = "群组成员不能为空")
    private List<Long> memberIds;
}
