package com.co.kc.imchat.service.message.facade.params;

import java.io.Serializable;

/**
 * 私聊会话删除请求。
 *
 * @param userId 用户 ID
 * @param friendUserId 对端用户 ID
 */
public record PrivateChatRemoveParams(Long userId, Long friendUserId) implements Serializable {
}
