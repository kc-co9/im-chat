package com.co.kc.imchat.application.model.cqrs.dto.group;

import lombok.Data;

@Data
public class GroupItemDTO {
    private Long groupId;
    private Long chatId;
    private String groupName;
    private Integer memberCount;
    private Integer unreadMessageCount;
}
