package com.co.kc.imchat.plugin.web.advice;

import com.co.kc.imchat.common.model.io.HttpResult;
import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.exception.RpcException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultAdviceTest {

    private final ResultAdvice advice = new ResultAdvice();

    @Test
    void wrapsPlainBodyWithHttpResult() throws Exception {
        Object body = advice.beforeBodyWrite(
                Map.of("name", "kc"),
                returnType("plainBody"),
                null,
                null,
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(body).isInstanceOf(HttpResult.class);
        assertThat(((HttpResult<?>) body).getData()).isEqualTo(Map.of("name", "kc"));
    }

    @Test
    void skipsStringReturnType() throws Exception {
        assertThat(advice.supports(returnType("stringBody"), StringHttpMessageConverter.class)).isFalse();
    }

    @Test
    void skipsResponseEntityReturnType() throws Exception {
        assertThat(advice.supports(returnType("responseEntity"), StringHttpMessageConverter.class)).isFalse();
    }

    @Test
    void keepsStringBodyUnchangedWhenDeclaredAsObject() throws Exception {
        Object body = advice.beforeBodyWrite(
                "raw",
                returnType("objectBody"),
                null,
                null,
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(body).isEqualTo("raw");
    }

    @Test
    void keepsResourceBodyUnchangedWhenDeclaredAsObject() throws Exception {
        ByteArrayResource resource = new ByteArrayResource("file".getBytes());

        Object body = advice.beforeBodyWrite(
                resource,
                returnType("objectBody"),
                null,
                null,
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(body).isSameAs(resource);
    }

    @Test
    void mapsUnreadableRequestBodyToParamsError() {
        ErrorAdvice errorAdvice = new ErrorAdvice();

        HttpResult<Map<String, Object>> result = errorAdvice.httpMessageNotReadableExceptionHandler(
                new HttpMessageNotReadableException("Required request body is missing"));

        assertThat(result.getCode()).isEqualTo(HttpErrorCode.PARAMS_ERROR.getCode());
    }

    @Test
    void preservesStableRpcErrorCodeAndMessage() {
        ErrorAdvice errorAdvice = new ErrorAdvice();

        HttpResult<?> result = errorAdvice.rpcExceptionHandler(
                new RpcException(10004, "用户不存在"));

        assertThat(result.getCode()).isEqualTo(10004);
        assertThat(result.getMsg()).isEqualTo("用户不存在");
    }

    private MethodParameter returnType(String methodName) throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod(methodName);
        return new MethodParameter(method, -1);
    }

    private static class SampleController {
        Map<String, Object> plainBody() {
            return Map.of();
        }

        String stringBody() {
            return "";
        }

        Object objectBody() {
            return null;
        }

        ResponseEntity<String> responseEntity() {
            return ResponseEntity.ok("");
        }
    }
}
