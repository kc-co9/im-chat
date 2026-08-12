package com.co.kc.imchat.service.social.facade.params;

import java.util.List;

/**
 * 查询好友展示信息请求。
 *
 * @param userId        当前用户 ID
 * @param friendUserIds 好友用户 ID 列表
 */
public record FriendDisplaysGetParams(Long userId, List<Long> friendUserIds) {
}
