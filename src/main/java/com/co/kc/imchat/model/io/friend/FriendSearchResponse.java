package com.co.kc.imchat.model.io.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendSearchResponse {
    private List<SearchItem> searchList;

    @Data
    public static class SearchItem {
        private Long userId;
        private String username;
    }

}
