package com.co.kc.imchat.model.cqrs.dto.im;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImGroupCreateDTO {
    private Long groupId;
    private Long chatId;
}
