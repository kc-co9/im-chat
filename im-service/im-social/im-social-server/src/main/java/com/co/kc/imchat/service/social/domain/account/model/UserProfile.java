package com.co.kc.imchat.service.social.domain.account.model;

/**
 * 社交服务使用的用户资料快照。
 *
 * @param userId   用户 ID
 * @param username 用户名
 * @param email    用户邮箱
 */
public record UserProfile(Long userId, String username, String email) {
}
