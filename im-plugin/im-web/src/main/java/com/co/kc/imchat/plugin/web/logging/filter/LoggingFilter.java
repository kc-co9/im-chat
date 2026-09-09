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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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
            stopWatch.stop();

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
}
