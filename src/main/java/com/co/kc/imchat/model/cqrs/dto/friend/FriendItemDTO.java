package com.co.kc.imchat.model.cqrs.dto.friend;

import com.co.kc.imchat.domain.friend.FriendStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendItemDTO {
    private Long userId;
    private String alias;
    private FriendStatus status;
    private LocalDateTime createTime;
}
