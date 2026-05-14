package com.co.kc.imchat.model.cqrs.dto.group;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GroupCreateDTO {
    private Long groupId;
    private Long chatId;
}
