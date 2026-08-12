package com.co.kc.imchat.service.social.facade.dto;

/**
 * 群摘要。
 *
 * @param groupId 群 ID
 * @param name    群名称
 * @param active  群是否有效
 */
public record GroupSummaryDTO(Long groupId, String name, boolean active) {
}
