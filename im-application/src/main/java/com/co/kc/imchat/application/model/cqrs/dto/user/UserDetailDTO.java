package com.co.kc.imchat.application.model.cqrs.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserDetailDTO
 *
 * @author kc
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDTO {
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 用户邮箱
     */
    private String email;
    /**
     * 用户名
     */
    private String username;
}
