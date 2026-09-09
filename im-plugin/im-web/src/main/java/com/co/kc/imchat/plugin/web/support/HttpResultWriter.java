package com.co.kc.imchat.plugin.web.support;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.model.io.HttpResult;
import com.co.kc.imchat.common.utils.JsonUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 将统一 HTTP 业务结果写入 Servlet 响应。 */
public final class HttpResultWriter {

    private HttpResultWriter() {
    }

    public static void write(HttpServletResponse response, HttpErrorCode errorCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJson(HttpResult.error(errorCode)));
    }
}
