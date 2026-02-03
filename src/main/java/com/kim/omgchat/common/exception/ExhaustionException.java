package com.kim.omgchat.common.exception;

import com.kim.omgchat.common.constant.ErrorCode;

/**
 * 资源耗尽异常
 *
 * @author kc
 */
public class ExhaustionException extends BaseException {
    public ExhaustionException(String reason) {
        super(ErrorCode.EXHAUSTION_ERROR, reason);
    }
}
