package com.co.kc.imchat.interfaces.support.model;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.interfaces.support.utils.HttpServletUtils;
import com.co.kc.imchat.interfaces.model.enums.LogFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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

@Data
@Slf4j
@Builder
public class HttpLog {
    private String traceId;
    private String requestMethod;
    private String requestPath;
    private String requestIp;
    private Map<String, String> requestHeaders;
    private Map<String, String> requestParameters;
    private String requestBody;
    private Integer responseStatus;
    private String responseTime;
    private Map<String, String> responseHeaders;
    private String responseBody;
    private Long durationMs;

    public static HttpLog newLog(HttpServletRequest request,
                                 HttpServletResponse response,
                                 String traceId,
                                 Long durationMs) {
        return HttpLog.builder()
                .traceId(traceId)
                .requestPath(request.getRequestURI())
                .requestMethod(request.getMethod())
                .requestIp(request.getRemoteAddr())
                .requestHeaders(HttpServletUtils.getHeader(request))
                .requestParameters(HttpServletUtils.getParameter(request))
                .requestBody(HttpServletUtils.getRequestBody(request))
                .responseStatus(HttpServletUtils.getResponseStatus(response))
                .responseTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .responseHeaders(HttpServletUtils.getHeader(response))
                .responseBody(HttpServletUtils.getResponseBody(response))
                .durationMs(durationMs)
                .build();
    }

    public static HttpLog newLog(HttpRequest httpRequest,
                                 ClientHttpResponse httpResponse,
                                 String traceId,
                                 byte[] body,
                                 Long durationMs) throws IOException {
        return HttpLog.builder()
                .traceId(traceId)
                .requestPath(httpRequest.getURI().toString())
                .requestMethod(httpRequest.getMethod().name())
                .requestHeaders(httpRequest.getHeaders().toSingleValueMap())
                .requestBody(new String(body, StandardCharsets.UTF_8))
                .responseStatus(httpResponse != null ? httpResponse.getStatusCode().value() : null)
                .responseTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .responseHeaders(httpResponse != null ? httpResponse.getHeaders().toSingleValueMap() : null)
                .responseBody(httpResponse != null ? StreamUtils.copyToString(httpResponse.getBody(), StandardCharsets.UTF_8) : null)
                .durationMs(durationMs)
                .build();
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
