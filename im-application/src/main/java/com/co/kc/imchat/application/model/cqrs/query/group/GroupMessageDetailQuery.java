package com.co.kc.imchat.application.model.cqrs.query.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessageDetailQuery {
    private Long chatId;
    private Long userId;
    private String messageToken;
}
