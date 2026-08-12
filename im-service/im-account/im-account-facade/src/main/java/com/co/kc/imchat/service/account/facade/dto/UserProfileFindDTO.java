package com.co.kc.imchat.service.account.facade.dto;

/**
 * 按邮箱查询用户资料响应。
 *
 * @param found    是否找到用户
 * @param userId   用户 ID
 * @param username 用户名
 * @param email    用户邮箱
 */
public record UserProfileFindDTO(boolean found, Long userId, String username, String email) {
    public static UserProfileFindDTO empty() {
        return new UserProfileFindDTO(false, null, null, null);
    }
}
