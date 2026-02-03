package com.kim.omgchat.model.cqrs.dto.friend;

import com.kim.omgchat.domain.friend.FriendStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendItemDTO {
    private Long userId;
    private String alias;
    private FriendStatus status;
    private LocalDateTime createTime;
}
