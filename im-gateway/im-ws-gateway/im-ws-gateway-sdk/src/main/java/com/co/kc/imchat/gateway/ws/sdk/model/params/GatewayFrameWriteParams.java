package com.co.kc.imchat.gateway.ws.sdk.model.params;

import com.co.kc.imchat.common.model.io.FrameResponse;

/**
 * 网关实时帧写入请求。
 *
 * @param userId 目标用户 ID
 * @param frame  写入客户端连接的实时帧
 */
public record GatewayFrameWriteParams(Long userId, FrameResponse frame) {
}
