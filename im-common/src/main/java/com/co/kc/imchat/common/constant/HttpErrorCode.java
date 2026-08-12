package com.co.kc.imchat.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * HTTP API 错误码。
 * <p>
 * 用于 HTTP 统一响应体和 HTTP 网关认证失败等接入层返回场景。
 */
@Getter
@AllArgsConstructor
public enum HttpErrorCode {
    /**
     * 系统通用错误。
     */
    SYS_ERROR(10000, "系统繁忙,请稍后重试"),
    AUTH_FAIL(10001, "认证失败，请重新授权"),
    AUTH_DENY(10002, "权限不足，请重新授权"),
    NOT_FOUND(10004, "查找失败，请稍后重试"),
    NETWORK_ERROR(10005, "网络错误,请稍后重试"),
    PARAMS_ERROR(10006, "参数错误,请稍后重试"),
    REPEATED_ERROR(10007, "重复错误,请稍后重试"),
    TIMEOUT_ERROR(10008, "超时错误,请稍后重试"),
    OPERATE_ERROR(10009, "操作错误,请稍后重试"),
    BUSY_ERROR(10010, "操作频繁,请稍后重试"),
    SERIALIZATION_ERROR(10011, "序列化错误,请稍后重试"),
    REFLECT_ERROR(10012, "系统异常,请稍后重试"),
    EXHAUSTION_ERROR(10013, "资源已耗尽,请稍后重试"),
    TRANSITION_ERROR(10014, "变更异常，请稍后重试"),
    ;

    private final int code;
    private final String msg;
}
