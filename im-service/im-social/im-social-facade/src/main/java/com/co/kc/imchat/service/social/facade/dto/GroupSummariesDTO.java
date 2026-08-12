package com.co.kc.imchat.service.social.facade.dto;

import java.util.List;

/**
 * 群摘要列表响应。
 *
 * @param groups 群摘要列表
 */
public record GroupSummariesDTO(List<GroupSummaryDTO> groups) {
}
