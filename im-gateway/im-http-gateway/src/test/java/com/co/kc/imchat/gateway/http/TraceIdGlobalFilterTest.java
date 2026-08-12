package com.co.kc.imchat.gateway.http;

import com.co.kc.imchat.common.constant.HttpHeaderConstants;
import com.co.kc.imchat.gateway.http.filter.TraceIdGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdGlobalFilterTest {

    @Test
    void addsTraceIdWhenMissing() {
        TraceIdGlobalFilter filter = new TraceIdGlobalFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user/userDetail"));
        AtomicReference<ServerWebExchange> nextExchange = new AtomicReference<>();

        filter.filter(exchange, capture(nextExchange)).block();

        assertThat(nextExchange.get().getRequest().getHeaders().getFirst(HttpHeaderConstants.TRACE_ID))
                .isNotBlank();
    }

    @Test
    void writesTraceIdToResponseWhenMissing() {
        TraceIdGlobalFilter filter = new TraceIdGlobalFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user/userDetail"));

        filter.filter(exchange, tracedExchange -> tracedExchange.getResponse().setComplete()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaderConstants.TRACE_ID))
                .isNotBlank();
    }

    @Test
    void keepsExistingTraceId() {
        TraceIdGlobalFilter filter = new TraceIdGlobalFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user/userDetail")
                        .header(HttpHeaderConstants.TRACE_ID, "trace-1"));
        AtomicReference<ServerWebExchange> nextExchange = new AtomicReference<>();

        filter.filter(exchange, capture(nextExchange)).block();

        assertThat(nextExchange.get().getRequest().getHeaders().getFirst(HttpHeaderConstants.TRACE_ID))
                .isEqualTo("trace-1");
    }

    @Test
    void writesExistingTraceIdToResponse() {
        TraceIdGlobalFilter filter = new TraceIdGlobalFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user/userDetail")
                        .header(HttpHeaderConstants.TRACE_ID, "trace-1"));

        filter.filter(exchange, tracedExchange -> tracedExchange.getResponse().setComplete()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaderConstants.TRACE_ID))
                .isEqualTo("trace-1");
    }

    private GatewayFilterChain capture(AtomicReference<ServerWebExchange> exchangeRef) {
        return exchange -> {
            exchangeRef.set(exchange);
            return Mono.empty();
        };
    }
}
