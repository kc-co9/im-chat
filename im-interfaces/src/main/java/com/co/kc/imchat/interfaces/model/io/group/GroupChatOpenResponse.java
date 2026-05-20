package com.co.kc.imchat.interfaces.model.io.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupChatOpenResponse {
    private Long chatId;
    private Long groupId;
}
