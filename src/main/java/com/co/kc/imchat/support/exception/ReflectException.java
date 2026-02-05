package com.co.kc.imchat.support.exception;

import com.co.kc.imchat.support.constant.ErrorCode;

/**
 * 反射异常
 *
 * @author kc
 */
public class ReflectException extends BaseException {
    public ReflectException(String reason, Throwable throwable) {
        super(ErrorCode.REFLECT_ERROR, reason, throwable);
    }
}
