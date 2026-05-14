package com.co.kc.imchat.model.cqrs.command.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInviteMembersCmd {
    private Long userId;
    private Long groupId;
    private List<Long> inviteeIds;
}
