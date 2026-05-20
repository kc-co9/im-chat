package com.co.kc.imchat.interfaces.support.web.filter;

import com.co.kc.imchat.interfaces.support.model.HttpLog;
import com.co.kc.imchat.interfaces.config.properties.LogProperties;
import com.co.kc.imchat.interfaces.support.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author kc
 */
@Slf4j
@Component
@WebFilter(urlPatterns = "/*")
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class LoggingFilter extends OncePerRequestFilter {

    private final LogProperties logProperties;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {
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

            if (!ServletFileUpload.isMultipartContent(request)) {
                HttpLog httpLog = HttpLog.newLog(request, response, LoggingUtils.getTraceId(), stopWatch.getTime(TimeUnit.MILLISECONDS));
                httpLog.print(logProperties.getLogFormat());
            }

            // 在过滤器中使用了 ContentCachingResponseWrapper 包装了原始的 HttpServletResponse。
            // ContentCachingResponseWrapper 会把响应的内容先缓存到内存里（缓存响应体），这样你可以多次读取响应内容，比如用来日志记录。
            // 但是，缓存的内容默认并不会自动写回给客户端，如果不手动调用 copyBodyToResponse()，客户端就收不到响应体数据，也就是浏览器等会拿到空响应或不完整的响应。
            // 所以，copyBodyToResponse() 是负责把缓存的内容写回到真正的响应流，确保客户端能正确收到响应体。
            copyBodyToResponse(response);
        }
    }

    private void copyBodyToResponse(HttpServletResponse response) throws IOException {
        ContentCachingResponseWrapper responseWrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        Objects.requireNonNull(responseWrapper).copyBodyToResponse();
    }


}
