package com.co.kc.imchat.plugin.web.advice;

import com.co.kc.imchat.common.model.io.HttpResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Collections;
import java.util.Optional;

/**
 * HTTP 统一返回体包装。
 */
@ConditionalOnWebApplication
@RestControllerAdvice(basePackages = {"com.co.kc.imchat"})
public class ResultAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return !isRawResponseType(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // 设置 Content-Type（不再需要显式指定 UTF-8，JSON 默认使用 UTF-8）
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (isRawResponseBody(body)) {
            return body;
        }
        if (body instanceof HttpResult) {
            @SuppressWarnings("unchecked")
            HttpResult<Object> result = (HttpResult<Object>) body;
            result.setData(decorateData(result.getData()));
            return body;
        }
        return HttpResult.success(decorateData(body));
    }

    private Object decorateData(Object data) {
        return Optional.ofNullable(data).orElse(Collections.emptyMap());
    }

    private boolean isRawResponseType(Class<?> type) {
        return String.class.isAssignableFrom(type)
                || byte[].class.isAssignableFrom(type)
                || Resource.class.isAssignableFrom(type)
                || StreamingResponseBody.class.isAssignableFrom(type)
                || ResponseEntity.class.isAssignableFrom(type);
    }

    private boolean isRawResponseBody(Object body) {
        return body instanceof String
                || body instanceof byte[]
                || body instanceof Resource
                || body instanceof StreamingResponseBody;
    }
}
