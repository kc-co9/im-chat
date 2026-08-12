package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.HttpErrorCode;

/**
 * 权限异常
 * <p>
 * 主要用于权限认证异常
 *
 * @author kc
 */
public class PermissionException extends BaseException {
    public PermissionException(String reason) {
        super(HttpErrorCode.AUTH_DENY, reason);
    }
}
