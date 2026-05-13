package com.co.kc.imchat.model.io.chat;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ImGroupDetailResponse {
    private Long groupId;
    private Long chatId;
    private String groupName;
    private Long ownerId;
    private String notification;
    private Integer memberCount;
    private List<Member> members;

    @Data
    public static class Member {
        private Long userId;
        private String userAlias;
        private LocalDateTime joinTime;
    }
}
