package com.co.kc.imchat.service.account.facade.params;

/**
 * 按邮箱查询用户资料请求。
 *
 * @param email 用户邮箱
 */
public record UserProfileFindParams(String email) {
}
