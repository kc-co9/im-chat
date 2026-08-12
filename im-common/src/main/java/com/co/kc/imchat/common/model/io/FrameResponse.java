package com.co.kc.imchat.common.model.io;

import com.co.kc.imchat.common.constant.FrameConstants;
import com.co.kc.imchat.common.constant.FrameErrorCode;
import com.co.kc.imchat.common.constant.FrameResultCode;
import com.co.kc.imchat.common.model.enums.FrameType;

import java.util.Map;

/**
 * 实时协议下行响应帧。
 * <p>
 * 网关响应、服务端推送和错误响应都属于服务端下行帧，用 type 区分具体语义。
 *
 * @param version 协议版本
 * @param type    下行帧类型
 * @param cmd     对应业务命令
 * @param seq     客户端请求序号
 * @param traceId 链路追踪 ID
 * @param code    处理结果码，成功为 0，失败为 -1
 * @param message 处理结果描述
 * @param body    业务响应体
 */
public record FrameResponse(
        String version,
        FrameType type,
        String cmd,
        String seq,
        String traceId,
        Integer code,
        String message,
        Map<String, Object> body
) {
    public static FrameResponse ok(FrameRequest request) {
        return new FrameResponse(request.version(), FrameType.RESPONSE, request.cmd(), request.seq(), request.traceId(),
                FrameResultCode.SUCCESS.code(), FrameResultCode.SUCCESS.message(), Map.of());
    }

    public static FrameResponse error(FrameRequest request, FrameErrorCode errorCode) {
        return error(request, errorCode, errorCode.message());
    }

    public static FrameResponse error(FrameRequest request, FrameErrorCode errorCode, String message) {
        return error(request, errorCode.code(), message);
    }

    public static FrameResponse error(FrameRequest request, String code, String message) {
        return new FrameResponse(request == null ? FrameConstants.DEFAULT_VERSION : request.version(), FrameType.ERROR,
                request == null ? null : request.cmd(),
                request == null ? null : request.seq(),
                request == null ? null : request.traceId(),
                FrameResultCode.ERROR.code(), message, Map.of(FrameConstants.ERROR_CODE_KEY, code));
    }
}
