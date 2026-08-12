package com.co.kc.imchat.service.social.model.cqrs.dto.friend;

import com.co.kc.imchat.service.social.domain.friend.model.FriendStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendItemDTO {
    private Long userId;
    private String displayName;
    private FriendStatus status;
    private LocalDateTime createTime;
}
