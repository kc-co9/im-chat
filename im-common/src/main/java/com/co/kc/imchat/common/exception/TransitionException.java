package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.HttpErrorCode;


public class TransitionException extends BaseException {
    public TransitionException(String reason) {
        super(HttpErrorCode.TRANSITION_ERROR, reason);
    }

    public TransitionException(String reason, Throwable throwable) {
        super(HttpErrorCode.TRANSITION_ERROR, reason, throwable);
    }
}
