package com.co.kc.imchat.model.cqrs.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupMessageDetailQuery {
    private Long chatId;
    private Long userId;
    private String messageToken;
}
