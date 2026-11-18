package com.co.kc.imchat.broker.support.context;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerManagementProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerWriteAccessContextInterceptorTest {
    @AfterEach
    void clearContext() {
        BrokerWriteAccessContextHolder.clear();
    }

    @Test
    void clearsInitialServletThreadWhenAsyncHandlingStarts() throws Exception {
        BrokerWriteAccessContextInterceptor interceptor =
                new BrokerWriteAccessContextInterceptor(new BrokerManagementProperties());
        BrokerWriteAccessContextHolder.set(new BrokerWriteAccessContext("operation-1", 11L));

        assertThat(interceptor).isInstanceOf(AsyncHandlerInterceptor.class);
        ((AsyncHandlerInterceptor) interceptor).afterConcurrentHandlingStarted(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertThat(BrokerWriteAccessContextHolder.get()).isNull();
    }

    @Test
    void synchronousCompletionAlsoClearsContext() throws Exception {
        BrokerWriteAccessContextInterceptor interceptor =
                new BrokerWriteAccessContextInterceptor(new BrokerManagementProperties());
        BrokerWriteAccessContextHolder.set(new BrokerWriteAccessContext("operation-1", 11L));

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(BrokerWriteAccessContextHolder.get()).isNull();
    }
}
