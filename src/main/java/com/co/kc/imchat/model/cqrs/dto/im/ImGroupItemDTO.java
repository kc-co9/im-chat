package com.co.kc.imchat.model.cqrs.dto.im;

import lombok.Data;

@Data
public class ImGroupItemDTO {
    private Long groupId;
    private Long chatId;
    private String groupName;
    private Integer unreadMessageCount;
}
