package com.kim.omgchat.common.exception;

import com.kim.omgchat.common.constant.ErrorCode;

/**
 * 已经存在异常
 *
 * @author kc
 */
public class RepeatException extends BaseException {
    public RepeatException(String reason) {
        super(ErrorCode.REPEATED_ERROR, reason);
    }
}
