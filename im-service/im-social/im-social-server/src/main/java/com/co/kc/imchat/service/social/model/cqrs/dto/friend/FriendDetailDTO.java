package com.co.kc.imchat.service.social.model.cqrs.dto.friend;

import com.co.kc.imchat.service.social.domain.friend.model.FriendStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendDetailDTO {
    private Long userId;
    private String username;
    private String alias;
    private FriendStatus status;
    private LocalDateTime createTime;
}
