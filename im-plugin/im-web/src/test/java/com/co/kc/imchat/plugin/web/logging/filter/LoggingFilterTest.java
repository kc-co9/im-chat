package com.co.kc.imchat.plugin.web.logging.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.co.kc.imchat.plugin.web.logging.HttpLog;
import com.co.kc.imchat.plugin.web.logging.LogProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingFilterTest {

    @Test
    void skipsCompiledFrontendResources() throws Exception {
        TestLoggingFilter filter = new TestLoggingFilter();

        assertThat(filter.skips("/favicon.ico")).isTrue();
        assertThat(filter.skips("/assets/app.js")).isTrue();
        assertThat(filter.skips("/assets/app.css")).isTrue();
        assertThat(filter.skips("/public/app.js")).isTrue();
        assertThat(filter.skips("/fonts/app.woff2")).isTrue();
        assertThat(filter.skips("/images/logo.svg")).isTrue();
        assertThat(filter.skips("/")).isFalse();
        assertThat(filter.skips("/index.html")).isTrue();
        assertThat(filter.skips("/api/report.json")).isFalse();
        assertThat(filter.skips("/iam/login")).isFalse();
        assertThat(filter.skips("/api/audits")).isFalse();
    }

    @Test
    void skipsResolvedMvcStaticResourceAtRoot() throws Exception {
        TestLoggingFilter filter = new TestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(HttpLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    servletRequest.setAttribute(
                            HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                            new ResourceHttpRequestHandler()));

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void logsControllerRequestMappedAtRoot() throws Exception {
        TestLoggingFilter filter = new TestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(HttpLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    servletRequest.setAttribute(
                            HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                            new Object()));

            assertThat(appender.list).singleElement();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static final class TestLoggingFilter extends LoggingFilter {
        private TestLoggingFilter() {
            super(new LogProperties());
        }

        private boolean skips(String path) throws Exception {
            return shouldNotFilter(new MockHttpServletRequest("GET", path));
        }
    }
}
