package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.HttpErrorCode;

/**
 * 不存在异常
 *
 * @author kc
 */
public class NotFoundException extends BaseException {
    public NotFoundException(String reason) {
        super(HttpErrorCode.NOT_FOUND, reason);
    }
}
