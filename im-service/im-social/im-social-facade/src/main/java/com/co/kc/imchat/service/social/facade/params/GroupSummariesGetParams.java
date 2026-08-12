package com.co.kc.imchat.service.social.facade.params;

import java.util.List;

/**
 * 查询群摘要请求。
 *
 * @param groupIds 群 ID 列表
 */
public record GroupSummariesGetParams(List<Long> groupIds) {
}
