package com.co.kc.imchat.common.exception;

import static com.co.kc.imchat.common.constant.ErrorCode.BUSY_ERROR;


/**
 * 锁异常
 */
public class LockException extends BaseException {

    public LockException(String reason) {
        super(BUSY_ERROR, reason);
    }

    public LockException(String reason, Throwable throwable) {
        super(BUSY_ERROR, reason, throwable);
    }

}
