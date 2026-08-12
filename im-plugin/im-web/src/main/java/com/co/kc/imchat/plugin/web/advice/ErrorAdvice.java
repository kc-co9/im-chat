package com.co.kc.imchat.plugin.web.advice;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.exception.BaseException;
import com.co.kc.imchat.common.model.io.HttpResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;
import java.util.Optional;

/**
 * HTTP 全局异常处理。
 */
@ControllerAdvice
@ResponseStatus(HttpStatus.OK)
public class ErrorAdvice {
    private static final Logger log = LoggerFactory.getLogger(ErrorAdvice.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public HttpResult<Map<String, Object>> illegalArgumentExceptionHandler(IllegalArgumentException ex) {
        log.error("IllegalArgumentException", ex);
        return HttpResult.error(HttpErrorCode.PARAMS_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public HttpResult<Map<String, Object>> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException ex) {
        log.error("MethodArgumentNotValidException", ex);
        String message = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(FieldError::getDefaultMessage)
                .orElse(HttpErrorCode.PARAMS_ERROR.getMsg());
        return HttpResult.error(HttpErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(BaseException.class)
    @ResponseBody
    public HttpResult<Map<String, Object>> baseExceptionHandler(BaseException ex) {
        log.error("BaseException", ex);
        return HttpResult.error(ex);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public HttpResult<Map<String, Object>> exceptionHandler(Exception ex) {
        log.error("Exception", ex);
        return HttpResult.error(HttpErrorCode.SYS_ERROR);
    }
}
