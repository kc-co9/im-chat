package com.co.kc.imchat.application.model.cqrs.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImPrivateMessageDetailQuery {
    private Long chatId;
    private Long userId;
    private String messageToken;
}
