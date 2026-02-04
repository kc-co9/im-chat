package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.ErrorCode;

/**
 * 认证异常
 * <p>
 * 主要用于登陆认证异常
 *
 * @author kc
 */
public class AuthException extends BaseException {
    public AuthException(String reason) {
        super(ErrorCode.AUTH_FAIL, reason);
    }

    public AuthException(ErrorCode errorCode, String reason) {
        super(errorCode, reason);
    }
}
