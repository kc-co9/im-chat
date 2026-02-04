package com.co.kc.imchat.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应成功的状态码，用于在成功状态下指定不同的
 *
 * @author kc
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    /**
     * 状态码
     */
    SUCCESS(0, "成功"),
    ;
    private final Integer code;
    private final String msg;
}
