package com.co.kc.imchat.model.cqrs.dto.im;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImPrivateChatEnterDTO {
    private Long chatId;
    private String chatName;
    private Long friendUserId;
    private String friendDisplayName;
}
