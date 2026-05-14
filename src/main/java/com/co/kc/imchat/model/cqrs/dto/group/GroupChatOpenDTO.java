package com.co.kc.imchat.model.cqrs.dto.group;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GroupChatOpenDTO {
    private Long chatId;
    private Long groupId;
}
