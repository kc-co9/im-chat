package com.co.kc.imchat.service.social.model.io.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupListResponse {
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
