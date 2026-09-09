package com.co.kc.imchat.plugin.dubbo.aspect;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.exception.BaseException;
import com.co.kc.imchat.common.exception.RpcException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 统一隔离 Dubbo Provider 的内部异常，只向调用方暴露稳定 RPC 错误。
 */
@Slf4j
@Aspect
public class RpcExceptionAspect {

    @Around("@within(org.apache.dubbo.config.annotation.DubboService)")
    public Object translate(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (RpcException exception) {
            throw exception;
        } catch (BaseException exception) {
            throw new RpcException(exception.getCode(), exception.getMsg());
        } catch (RuntimeException exception) {
            log.error("RPC Provider execution failed, method: {}", joinPoint.getSignature(), exception);
            throw new RpcException(HttpErrorCode.SYS_ERROR.getCode(), HttpErrorCode.SYS_ERROR.getMsg());
        }
    }
}
