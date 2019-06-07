package com.kim.omgchat.dto;

import lombok.Data;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 23:23
 */
@Data
public class UserFriendListDTO {
    private Long userId;
    private String nickname;
    /** 用户状态 online offline **/
    private String status;
}
