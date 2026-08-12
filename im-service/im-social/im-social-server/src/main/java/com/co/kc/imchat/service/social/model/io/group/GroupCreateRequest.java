package com.co.kc.imchat.service.social.model.io.group;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class GroupCreateRequest {
    @NotEmpty(message = "群组成员不能为空")
    private List<Long> memberIds;
    @NotEmpty(message = "群名不能为空")
    private String groupName;
}
