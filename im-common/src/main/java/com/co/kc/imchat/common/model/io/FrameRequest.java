package com.co.kc.imchat.common.model.io;

import java.util.Map;

/**
 * 实时协议上行请求帧。
 *
 * @param version 协议版本
 * @param cmd     业务命令
 * @param seq     客户端请求序号
 * @param traceId 链路追踪 ID
 * @param body    业务请求体
 */
public record FrameRequest(
        String version,
        String cmd,
        String seq,
        String traceId,
        Map<String, Object> body
) {
}
