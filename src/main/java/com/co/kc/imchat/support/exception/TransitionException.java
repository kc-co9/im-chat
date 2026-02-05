package com.co.kc.imchat.support.exception;

import com.co.kc.imchat.support.constant.ErrorCode;


public class TransitionException extends BaseException {
    public TransitionException(String reason) {
        super(ErrorCode.TRANSITION_ERROR, reason);
    }

    public TransitionException(String reason, Throwable throwable) {
        super(ErrorCode.TRANSITION_ERROR, reason, throwable);
    }
}
