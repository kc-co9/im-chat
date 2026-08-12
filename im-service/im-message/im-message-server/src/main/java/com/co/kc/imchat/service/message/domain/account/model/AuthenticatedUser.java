package com.co.kc.imchat.service.message.domain.account.model;

/**
 * 账号服务认证后的用户资料快照。
 */
public record AuthenticatedUser(Long userId, String email, String username) {
}
