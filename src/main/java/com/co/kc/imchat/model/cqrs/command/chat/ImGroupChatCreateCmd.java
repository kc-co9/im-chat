package com.co.kc.imchat.model.cqrs.command.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupChatCreateCmd {
    private Long ownerId;
    private List<Long> memberIds;
    private String groupName;
}
