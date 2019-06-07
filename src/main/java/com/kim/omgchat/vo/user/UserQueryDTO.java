package com.kim.omgchat.vo.user;

import lombok.Data;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 17:10
 */
@Data
public class UserQueryDTO {
    private Long userId;

    private String email;

    private String token;
}
