package com.co.kc.imchat.management.iam.sdk.interfaces.http;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 当前请求未建立有效 IAM 应用会话。 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class IamUnauthenticatedException extends RuntimeException {
    public IamUnauthenticatedException(String message) {
        super(message);
    }
}
