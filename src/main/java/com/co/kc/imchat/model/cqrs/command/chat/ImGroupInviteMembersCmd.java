package com.co.kc.imchat.model.cqrs.command.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupInviteMembersCmd {
    private Long userId;
    private Long groupId;
    private List<Long> inviteeIds;
}
