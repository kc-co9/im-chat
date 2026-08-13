package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;
import java.util.Set;

/**
 * 查询用户多个群聊会话请求。
 *
 * @param userId   用户 ID
 * @param groupIds 群 ID 集合
 */
public record UserGroupChatSummariesGetParams(Long userId, Set<Long> groupIds) implements Serializable {
}
