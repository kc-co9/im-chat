package com.co.kc.imchat.model.io.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupCreateResponse {
    private Long groupId;
    private Long chatId;
}
