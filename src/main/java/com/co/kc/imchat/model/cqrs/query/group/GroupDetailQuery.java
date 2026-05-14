package com.co.kc.imchat.model.cqrs.query.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupDetailQuery {
    private Long userId;
    private Long groupId;
}
