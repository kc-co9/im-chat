package com.co.kc.imchat.plugin.web.logging.filter;

import com.co.kc.imchat.plugin.web.logging.HttpLog;
import com.co.kc.imchat.plugin.web.logging.LogProperties;
import com.co.kc.imchat.plugin.web.logging.LoggingUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/**
 * @author kc
 */
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class LoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    private static final Set<String> STATIC_RESOURCE_EXTENSIONS = Set.of(
            "avif", "css", "eot", "gif", "html", "ico", "jpeg", "jpg", "js",
            "map", "png", "svg", "ttf", "webp", "woff", "woff2");

    private final LogProperties logProperties;

    public LoggingFilter(LogProperties logProperties) {
        this.logProperties = logProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String extension = StringUtils.getFilenameExtension(path);
        return path.startsWith("/assets/")
                || (extension != null && STATIC_RESOURCE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        StopWatch stopWatch = StopWatch.createStarted();

        if (!(request instanceof ContentCachingRequestWrapper)) {
            request = new ContentCachingRequestWrapper(request, 10 * 1024);
        }
        if (!(response instanceof ContentCachingResponseWrapper)) {
            response = new ContentCachingResponseWrapper(response);
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.error("异常请求 | 请求唯一id:[{}] | path:[{}] | error:[{}]", LoggingUtils.getTraceId(), request.getRequestURI(), ex.getMessage(), ex);
            throw ex;
        } finally {
            if (request.isAsyncStarted()) {
                request.getAsyncContext().addListener(
                        new LoggingAsyncListener(request, response, stopWatch));
            } else {
                completeRequest(request, response, stopWatch);
            }
        }
    }

    private void completeRequest(HttpServletRequest request, HttpServletResponse response,
                                 StopWatch stopWatch) throws IOException {
        if (stopWatch.isStarted()) {
            stopWatch.stop();
        }
        if (!isMultipartContent(request) && !isStaticResourceRequest(request)) {
            HttpLog httpLog = HttpLog.newLog(
                    request,
                    response,
                    LoggingUtils.getTraceId(),
                    stopWatch.getTime(TimeUnit.MILLISECONDS));
            httpLog.print(logProperties.getLogFormat());
        }
        copyBodyToResponse(response);
    }

    private void copyBodyToResponse(HttpServletResponse response) throws IOException {
        ContentCachingResponseWrapper responseWrapper = WebUtils.getNativeResponse(
                response,
                ContentCachingResponseWrapper.class);
        Objects.requireNonNull(responseWrapper).copyBodyToResponse();
    }

    private boolean isMultipartContent(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private boolean isStaticResourceRequest(HttpServletRequest request) {
        Object handler = request.getAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        return handler instanceof ResourceHttpRequestHandler;
    }

    private final class LoggingAsyncListener implements AsyncListener {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final StopWatch stopWatch;
        private final AtomicBoolean completed = new AtomicBoolean();

        private LoggingAsyncListener(HttpServletRequest request, HttpServletResponse response,
                                     StopWatch stopWatch) {
            this.request = request;
            this.response = response;
            this.stopWatch = stopWatch;
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            if (completed.compareAndSet(false, true)) {
                completeRequest(request, response, stopWatch);
            }
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // Servlet 容器会在超时处理结束后调用 onComplete，统一在那里提交缓存响应。
        }

        @Override
        public void onError(AsyncEvent event) {
            // Servlet 容器会在错误分派结束后调用 onComplete，避免提前提交错误响应。
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}
