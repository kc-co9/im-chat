package com.co.kc.imchat.plugin.web.logging.interceptor;

import com.co.kc.imchat.plugin.web.logging.HttpLog;
import com.co.kc.imchat.plugin.web.logging.LogProperties;
import com.co.kc.imchat.plugin.web.logging.LoggingUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(HttpLoggingInterceptor.class);

    private final Long slowTimeMs;
    private final LogProperties logProperties;

    public HttpLoggingInterceptor(long slowTimeMs, LogProperties logProperties) {
        this.slowTimeMs = slowTimeMs;
        this.logProperties = logProperties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest httpRequest, byte[] body, ClientHttpRequestExecution execution) throws IOException {
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

            if (httpLog.durationMs() > slowTimeMs) {
                log.warn("慢请求【{}】，执行时间为:【{}】毫秒", httpLog.requestPath(), httpLog.durationMs());
            }
        }
    }
}
