package com.co.kc.imchat.gateway.ws.protocol;

import com.co.kc.imchat.common.exception.SerializationException;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.utils.JsonUtils;

/**
 * IM 实时协议帧 JSON 编解码工具。
 */
public final class JsonFrameCodec {

    private JsonFrameCodec() {
    }

    public static FrameRequest decodeRequest(String payload) {
        try {
            return JsonUtils.fromJson(payload, FrameRequest.class);
        } catch (SerializationException ex) {
            throw new IllegalArgumentException("Invalid frame request payload", ex);
        }
    }

    public static FrameResponse decodeResponse(String payload) {
        try {
            return JsonUtils.fromJson(payload, FrameResponse.class);
        } catch (SerializationException ex) {
            throw new IllegalArgumentException("Invalid frame response payload", ex);
        }
    }

    public static String encodeResponse(FrameResponse frame) {
        try {
            return JsonUtils.toJson(frame);
        } catch (SerializationException ex) {
            throw new IllegalArgumentException("Invalid frame response object", ex);
        }
    }
}
