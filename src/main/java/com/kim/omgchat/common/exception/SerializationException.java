package com.kim.omgchat.common.exception;

import com.kim.omgchat.common.constant.ErrorCode;

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
