package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.model.io.HttpResult;
import com.co.kc.imchat.management.iam.sdk.interfaces.http.IamUnauthenticatedException;
import com.co.kc.imchat.plugin.web.support.HttpResultWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.util.Map;

/** 统一 IAM 认证入口、权限拒绝和方法级授权失败的 HTTP 响应。 */
@RestControllerAdvice(basePackages = "com.co.kc.imchat")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IamSecurityExceptionHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        HttpResultWriter.write(response, HttpErrorCode.AUTH_FAIL);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        HttpResultWriter.write(response, HttpErrorCode.AUTH_DENY);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public HttpResult<Map<String, Object>> authorizationDenied(
            AuthorizationDeniedException exception
    ) {
        return HttpResult.error(HttpErrorCode.AUTH_DENY);
    }

    @ExceptionHandler(IamUnauthenticatedException.class)
    public HttpResult<Map<String, Object>> unauthenticated(
            IamUnauthenticatedException exception
    ) {
        return HttpResult.error(HttpErrorCode.AUTH_FAIL);
    }
}
