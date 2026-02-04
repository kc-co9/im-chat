package com.kim.omgchat.infrastructure.advice;

import com.kim.omgchat.model.io.Result;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collections;
import java.util.Optional;

/**
 * @author kc
 */
@ConditionalOnWebApplication
@RestControllerAdvice(basePackages = {"com.kim.omgchat"})
public class ResultAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(@NotNull MethodParameter returnType, @NotNull Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, @NotNull MethodParameter returnType, @NotNull MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        // 设置 Content-Type（不再需要显式指定 UTF-8，JSON 默认使用 UTF-8）
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (body instanceof Result) {
            @SuppressWarnings("unchecked")
            Result<Object> result = (Result<Object>) body;
            result.setData(decorateData(result.getData()));
            return body;
        }
        return Result.success(decorateData(body));
    }

    private Object decorateData(Object data) {
        return Optional.ofNullable(data).orElse(Collections.emptyMap());
    }
}
