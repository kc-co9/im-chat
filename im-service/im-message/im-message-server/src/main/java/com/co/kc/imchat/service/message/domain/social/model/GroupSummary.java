package com.co.kc.imchat.service.message.domain.social.model;

/**
 * 群摘要。
 *
 * @param groupId 群 ID
 * @param name    群名称
 * @param active  群是否有效
 */
public record GroupSummary(Long groupId, String name, boolean active) {
}
