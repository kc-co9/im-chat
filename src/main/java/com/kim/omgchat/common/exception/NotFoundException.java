package com.kim.omgchat.common.exception;

import com.kim.omgchat.common.constant.ErrorCode;

/**
 * 不存在异常
 *
 * @author kc
 */
public class NotFoundException extends BaseException {
    public NotFoundException(String reason) {
        super(ErrorCode.NOT_FOUND, reason);
    }
}
