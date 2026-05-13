package com.co.kc.imchat.model.io.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupListResponse {
    private List<GroupItem> groupList;

    @Data
    public static class GroupItem {
        private Long groupId;
        private Long chatId;
        private String groupName;
        private Long ownerId;
        private Integer memberCount;
        private Integer unreadMessageCount;
    }
}
