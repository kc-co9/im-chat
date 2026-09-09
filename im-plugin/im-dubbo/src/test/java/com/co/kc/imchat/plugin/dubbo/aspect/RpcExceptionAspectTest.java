package com.co.kc.imchat.plugin.dubbo.aspect;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RpcException;
import example.RpcExceptionAspectFixture;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RpcExceptionAspectTest {
    private final RpcExceptionAspect aspect = new RpcExceptionAspect();

    @Test
    void mapsBusinessFailureToStableRpcErrorWithoutInternalReason() throws Throwable {
        ProceedingJoinPoint joinPoint = failingJoinPoint(new NotFoundException("internal detail"));

        assertThatThrownBy(() -> aspect.translate(joinPoint))
                .isInstanceOfSatisfying(RpcException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(HttpErrorCode.NOT_FOUND.getCode());
                    assertThat(exception).hasMessage(HttpErrorCode.NOT_FOUND.getMsg());
                });
    }

    @Test
    void preservesExistingRpcFailure() throws Throwable {
        RpcException failure = new RpcException(
                HttpErrorCode.BUSY_ERROR.getCode(), HttpErrorCode.BUSY_ERROR.getMsg());
        ProceedingJoinPoint joinPoint = failingJoinPoint(failure);

        assertThatThrownBy(() -> aspect.translate(joinPoint)).isSameAs(failure);
    }

    @Test
    void hidesUnexpectedProviderFailure() throws Throwable {
        ProceedingJoinPoint joinPoint = failingJoinPoint(new IllegalStateException("secret detail"));

        assertThatThrownBy(() -> aspect.translate(joinPoint))
                .isInstanceOfSatisfying(RpcException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(HttpErrorCode.SYS_ERROR.getCode());
                    assertThat(exception).hasMessage(HttpErrorCode.SYS_ERROR.getMsg());
                });
    }

    @Test
    void interceptsDubboServiceThroughGenericPointcut() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new RpcExceptionAspectFixture.Service());
        factory.addAspect(aspect);
        RpcExceptionAspectFixture.Contract service = factory.getProxy();

        assertThatThrownBy(service::execute)
                .isInstanceOfSatisfying(RpcException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(HttpErrorCode.NOT_FOUND.getCode()));
    }

    private static ProceedingJoinPoint failingJoinPoint(RuntimeException failure) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(failure);
        return joinPoint;
    }

}
