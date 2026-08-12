package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.HttpErrorCode;

/**
 * 资源耗尽异常
 *
 * @author kc
 */
public class ExhaustionException extends BaseException {
    public ExhaustionException(String reason) {
        super(HttpErrorCode.EXHAUSTION_ERROR, reason);
    }
}
