package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.HttpErrorCode;

/**
 * 反射异常
 *
 * @author kc
 */
public class ReflectException extends BaseException {
    public ReflectException(String reason, Throwable throwable) {
        super(HttpErrorCode.REFLECT_ERROR, reason, throwable);
    }
}
