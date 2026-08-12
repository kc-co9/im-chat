package com.co.kc.imchat.common.constant;

/**
 * 实时帧处理结果码。
 * <p>
 * 只表达协议层处理是否成功，具体失败原因由错误帧 body 中的业务错误码描述。
 */
public enum FrameResultCode {
    /**
     * 实时帧处理成功。
     */
    SUCCESS(0, "OK"),

    /**
     * 实时帧处理失败。
     */
    ERROR(-1, null);

    private final Integer code;
    private final String message;

    FrameResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer code() {
        return code;
    }

    public String message() {
        return message;
    }
}
