package com.co.kc.imchat.support.web.interceptor;

import com.co.kc.imchat.support.model.HttpLog;
import com.co.kc.imchat.infrastructure.config.properties.LogProperties;
import com.co.kc.imchat.support.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final Long slowTimeMs;
    private final LogProperties logProperties;

    public HttpLoggingInterceptor(long slowTimeMs, LogProperties logProperties) {
        this.slowTimeMs = slowTimeMs;
        this.logProperties = logProperties;
    }

    @NotNull
    @Override
    public ClientHttpResponse intercept(@NotNull HttpRequest httpRequest, @NotNull byte[] body, ClientHttpRequestExecution execution) throws IOException {
        StopWatch stopwatch = new StopWatch();
        ClientHttpResponse httpResponse = null;
        try {
            stopwatch.start();
            httpResponse = execution.execute(httpRequest, body);
            return httpResponse;
        } finally {
            stopwatch.stop();

            HttpLog httpLog = HttpLog.newLog(httpRequest, httpResponse,
                    LoggingUtils.getTraceId(), body, stopwatch.getTime(TimeUnit.MILLISECONDS));
            httpLog.print(logProperties.getLogFormat());

            if (httpLog.getDurationMs() > slowTimeMs) {
                log.warn("慢请求【{}】，执行时间为:【{}】毫秒", httpLog.getRequestPath(), httpLog.getDurationMs());
            }
        }
    }
}
