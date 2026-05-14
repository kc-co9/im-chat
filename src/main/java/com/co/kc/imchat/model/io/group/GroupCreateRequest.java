package com.co.kc.imchat.model.io.group;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class GroupCreateRequest {
    @NotEmpty(message = "群组成员不能为空")
    private List<Long> memberIds;
    @NotEmpty(message = "群名不能为空")
    private String groupName;
}
