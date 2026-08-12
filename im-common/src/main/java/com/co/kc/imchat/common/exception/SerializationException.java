package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.HttpErrorCode;

/**
 * 序列化异常
 *
 * @author kc
 */
public class SerializationException extends BaseException {
    public SerializationException(String reason, Throwable throwable) {
        super(HttpErrorCode.SERIALIZATION_ERROR, reason, throwable);
    }
}
