package com.co.kc.imchat.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * HTTP API 成功结果码。
 * <p>
 * 用于 HTTP 统一响应体中的成功状态。
 */
@Getter
@AllArgsConstructor
public enum HttpResultCode {
    /**
     * 请求处理成功。
     */
    SUCCESS(0, "成功"),
    ;
    private final Integer code;
    private final String msg;
}
