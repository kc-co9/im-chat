package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.ErrorCode;

/**
 * HTTP请求异常
 * <p>
 * 主要用于HTTP请求异常
 *
 * @author kc
 */
public class HttpException extends BaseException {
    public HttpException(String reason) {
        super(ErrorCode.NETWORK_ERROR, reason);
    }

    public HttpException(String reason, Throwable throwable) {
        super(ErrorCode.NETWORK_ERROR, reason, throwable);
    }
}
