package com.co.kc.imchat.support.exception;

import com.co.kc.imchat.support.constant.ErrorCode;

/**
 * 序列化异常
 *
 * @author kc
 */
public class SerializationException extends BaseException {
    public SerializationException(String reason, Throwable throwable) {
        super(ErrorCode.SERIALIZATION_ERROR, reason, throwable);
    }
}
