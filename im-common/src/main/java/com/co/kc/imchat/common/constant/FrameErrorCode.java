package com.co.kc.imchat.common.constant;

/**
 * 实时协议错误码。
 * <p>
 * 用于错误响应帧中统一表达处理失败原因。
 */
public enum FrameErrorCode {
    BAD_FRAME("BAD_FRAME", "非法消息帧"),
    UNAUTHORIZED("UNAUTHORIZED", "连接未认证"),
    UNKNOWN_CMD("UNKNOWN_CMD", "不支持的实时命令"),
    BAD_REQUEST("BAD_REQUEST", "请求参数错误"),
    BROKER_UNAVAILABLE("BROKER_UNAVAILABLE", "消息服务暂不可用");

    private final String code;
    private final String message;

    FrameErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
