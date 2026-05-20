package com.co.kc.imchat.application.model.cqrs.command.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupDismissCmd {
    private Long userId;
    private Long groupId;
}
