package com.co.kc.imchat.model.io.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendListResponse {
    private List<FriendItem> friends;

    @Data
    public static class FriendItem {
        private Long userId;
        private String alias;
        private LocalDateTime createTime;
    }
}
