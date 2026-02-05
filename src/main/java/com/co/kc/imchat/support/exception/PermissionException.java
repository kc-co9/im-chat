package com.co.kc.imchat.support.exception;

import com.co.kc.imchat.support.constant.ErrorCode;

/**
 * 权限异常
 * <p>
 * 主要用于权限认证异常
 *
 * @author kc
 */
public class PermissionException extends BaseException {
    public PermissionException(String reason) {
        super(ErrorCode.AUTH_DENY, reason);
    }
}
