package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.domain.user.model.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MemberDescriptor {
    private UserId userId;
    private MemberDisplayName displayName;
    private LocalDateTime joinTime;
}
