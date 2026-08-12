package com.co.kc.imchat.plugin.web.logging;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.web.support.HttpServletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public record HttpLog(
        String traceId,
        String requestMethod,
        String requestPath,
        String requestIp,
        Map<String, String> requestHeaders,
        Map<String, String> requestParameters,
        String requestBody,
        Integer responseStatus,
        String responseTime,
        Map<String, String> responseHeaders,
        String responseBody,
        Long durationMs
) {
    private static final Logger log = LoggerFactory.getLogger(HttpLog.class);

    public static HttpLog newLog(HttpServletRequest request,
                                 HttpServletResponse response,
                                 String traceId,
                                 Long durationMs) {
        return new HttpLog(
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                HttpServletUtils.getHeader(request),
                HttpServletUtils.getParameter(request),
                HttpServletUtils.getRequestBody(request),
                HttpServletUtils.getResponseStatus(response),
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                HttpServletUtils.getHeader(response),
                HttpServletUtils.getResponseBody(response),
                durationMs);
    }

    public static HttpLog newLog(HttpRequest httpRequest,
                                 ClientHttpResponse httpResponse,
                                 String traceId,
                                 byte[] body,
                                 Long durationMs) throws IOException {
        return new HttpLog(
                traceId,
                httpRequest.getMethod().name(),
                httpRequest.getURI().toString(),
                null,
                httpRequest.getHeaders().toSingleValueMap(),
                null,
                new String(body, StandardCharsets.UTF_8),
                httpResponse != null ? httpResponse.getStatusCode().value() : null,
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                httpResponse != null ? httpResponse.getHeaders().toSingleValueMap() : null,
                httpResponse != null ? StreamUtils.copyToString(httpResponse.getBody(), StandardCharsets.UTF_8) : null,
                durationMs);
    }

    public void print(LogFormat format) {
        log.info("请求/响应日志: {}", LogFormat.JSON.equals(format) ? toJsonLog() : toPrettyLog());
    }

    public String toJsonLog() {
        return JsonUtils.toJson(this);
    }

    public String toPrettyLog() {
        return "\n================== 请求响应日志 ==================\n" +
                buildLogLine("请求id", traceId) +
                buildLogLine("请求路径", requestPath) +
                buildLogLine("请求方法", requestMethod) +
                buildLogLine("请求IP", requestIp) +
                buildLogLine("请求头", JsonUtils.toJson(requestHeaders)) +
                buildLogLine("请求参数", JsonUtils.toJson(requestParameters)) +
                buildLogLine("请求Body", requestBody) +
                buildLogLine("响应状态", String.valueOf(responseStatus)) +
                buildLogLine("响应时间", responseTime) +
                buildLogLine("响应头", JsonUtils.toJson(responseHeaders)) +
                buildLogLine("响应Body", responseBody) +
                buildLogLine("响应耗时", String.format("%sms", durationMs)) +
                "===================================================";
    }

    private StringBuilder buildLogLine(String name, String value) {
        return new StringBuilder()
                .append(name)
                .append(":")
                .append(value)
                .append(System.lineSeparator());
    }
}
