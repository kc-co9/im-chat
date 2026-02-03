package com.kim.omgchat.infrastructure.advice;


import com.kim.omgchat.common.constant.ErrorCode;
import com.kim.omgchat.common.exception.BaseException;
import com.kim.omgchat.model.io.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Optional;

/**
 * @author kc
 */
@Slf4j
@RestControllerAdvice
@ResponseStatus(HttpStatus.OK)
public class ErrorAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Map<String, Object>> illegalArgumentExceptionHandler(IllegalArgumentException ex) {
        log.error("IllegalArgumentException", ex);
        return Result.error(ErrorCode.PARAMS_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Map<String, Object>> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException ex) {
        log.error("MethodArgumentNotValidException", ex);
        String message = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAMS_ERROR.getMsg());
        return Result.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(BaseException.class)
    public Result<Map<String, Object>> baseExceptionHandler(BaseException ex) {
        log.error("BaseException", ex);
        return Result.error(ex);
    }

    @ExceptionHandler(Exception.class)
    public Result<Map<String, Object>> exceptionHandler(Exception ex) {
        log.error("Exception", ex);
        return Result.error(ErrorCode.SYS_ERROR);
    }

    @MessageExceptionHandler(BaseException.class)
    @SendToUser("/queue/result")
    public Result<?> messageExceptionHandler(BaseException ex) {
        log.error("BaseException", ex);
        return Result.error(ex);
    }

}
