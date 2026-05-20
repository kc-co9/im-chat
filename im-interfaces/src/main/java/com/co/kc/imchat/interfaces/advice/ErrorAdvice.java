package com.co.kc.imchat.interfaces.advice;


import com.co.kc.imchat.common.constant.ErrorCode;
import com.co.kc.imchat.common.exception.BaseException;
import com.co.kc.imchat.interfaces.support.websocket.PushQueue;
import com.co.kc.imchat.common.model.io.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;
import java.util.Optional;

/**
 * @author kc
 */
@Slf4j
@ControllerAdvice
@ResponseStatus(HttpStatus.OK)
public class ErrorAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public Result<Map<String, Object>> illegalArgumentExceptionHandler(IllegalArgumentException ex) {
        log.error("IllegalArgumentException", ex);
        return Result.error(ErrorCode.PARAMS_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public Result<Map<String, Object>> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException ex) {
        log.error("MethodArgumentNotValidException", ex);
        String message = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAMS_ERROR.getMsg());
        return Result.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(BaseException.class)
    @ResponseBody
    public Result<Map<String, Object>> baseExceptionHandler(BaseException ex) {
        log.error("BaseException", ex);
        return Result.error(ex);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result<Map<String, Object>> exceptionHandler(Exception ex) {
        log.error("Exception", ex);
        return Result.error(ErrorCode.SYS_ERROR);
    }

    @MessageExceptionHandler(BaseException.class)
    @SendToUser(PushQueue.QUEUE_RESULT)
    public Result<?> messageExceptionHandler(BaseException ex) {
        log.error("BaseException", ex);
        return Result.error(ex);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(PushQueue.QUEUE_RESULT)
    public Result<?> messageExceptionHandler(Exception ex) {
        log.error("Exception", ex);
        return Result.error(ErrorCode.SYS_ERROR);
    }


}
