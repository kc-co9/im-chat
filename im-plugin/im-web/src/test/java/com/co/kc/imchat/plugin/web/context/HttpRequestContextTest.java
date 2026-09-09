package com.co.kc.imchat.plugin.web.context;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import jakarta.servlet.FilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestContextTest {

    @AfterEach
    void clearContext() {
        HttpRequestContextHolder.clear();
    }

    @Test
    void establishesAndClearsRequestMetadata() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "browser");
        HttpRequestContextFilter filter = new HttpRequestContextFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> assertThat(HttpRequestContextHolder.get())
                .contains(new HttpRequestContext("127.0.0.1", "browser"));

        filter.doFilter(request, response, chain);

        assertThat(HttpRequestContextHolder.get()).isEmpty();
    }

    @Test
    void normalizesMissingMetadataAndLimitsUserAgentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");
        request.addHeader("User-Agent", "a".repeat(300));
        HttpRequestContextFilter filter = new HttpRequestContextFilter();
        FilterChain chain = (servletRequest, servletResponse) -> {
            HttpRequestContext context = HttpRequestContextHolder.get().orElseThrow();
            assertThat(context.clientAddress()).isEqualTo("unknown");
            assertThat(context.userAgent()).hasSize(255);
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void propagatesContextThroughTtlExecutor() throws Exception {
        ExecutorService executor = TtlExecutors.getTtlExecutorService(
                Executors.newSingleThreadExecutor());
        try {
            HttpRequestContext context = new HttpRequestContext("127.0.0.1", "browser");
            HttpRequestContextHolder.set(context);

            assertThat(executor.submit(HttpRequestContextHolder::get).get())
                    .contains(context);
        } finally {
            executor.shutdownNow();
        }
    }
}
