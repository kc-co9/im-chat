package com.kim.omgchat.model.cqrs.command.im;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ImGroupChatCreateCmd {
    private Long ownerId;
    private List<Long> memberIds;
    private String groupName;
}
