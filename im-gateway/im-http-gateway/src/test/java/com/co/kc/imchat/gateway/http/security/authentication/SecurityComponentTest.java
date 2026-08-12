package com.co.kc.imchat.gateway.http.security.authentication;

import com.co.kc.imchat.gateway.http.security.filter.UserContextHeaderSanitizingFilter;
import com.co.kc.imchat.plugin.session.context.UserContextHeaders;
import com.co.kc.imchat.plugin.session.token.TokenDTO;
import com.co.kc.imchat.plugin.session.token.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityComponentTest {

    @Test
    void converterReadsBearerToken() {
        AuthenticationConverter converter = new AuthenticationConverter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/friend/friendList")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"));

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication).isInstanceOf(AuthenticationToken.class);
        assertThat(((AuthenticationToken) authentication).getToken()).isEqualTo("valid-token");
    }

    @Test
    void managerAuthenticatesTokenWithUserId() {
        AuthenticationManager manager = new AuthenticationManager(List.of(tokenService()));

        Authentication authentication = manager.authenticate(new AuthenticationToken("valid-token")).block();

        assertThat(authentication).isInstanceOf(AuthenticationToken.class);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(((AuthenticationToken) authentication).getUserId()).isEqualTo(42L);
    }

    @Test
    void managerRejectsInvalidToken() {
        AuthenticationManager manager = new AuthenticationManager(List.of(tokenService()));

        assertThatThrownBy(() -> manager.authenticate(new AuthenticationToken("bad-token")).block())
                .hasMessageContaining("Invalid token");
    }

    @Test
    void successHandlerAddsTrustedUserHeaderAndRemovesSpoofedHeader() {
        AuthenticationSuccessHandler successHandler = new AuthenticationSuccessHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/friend/friendList")
                .header(UserContextHeaders.USER_ID, "999"));
        AtomicReference<ServerWebExchange> downstreamExchange = new AtomicReference<>();
        WebFilterChain chain = filteredExchange -> {
            downstreamExchange.set(filteredExchange);
            return Mono.empty();
        };

        successHandler.onAuthenticationSuccess(
                new WebFilterExchange(exchange, chain),
                new AuthenticationToken("valid-token", 42L)).block();

        assertThat(downstreamExchange.get().getRequest().getHeaders().getFirst(UserContextHeaders.USER_ID))
                .isEqualTo("42");
    }

    @Test
    void sanitizingFilterRemovesExternalUserHeader() {
        UserContextHeaderSanitizingFilter filter = new UserContextHeaderSanitizingFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/user/signIn")
                .header(UserContextHeaders.USER_ID, "999"));
        AtomicReference<ServerWebExchange> downstreamExchange = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            downstreamExchange.set(filteredExchange);
            return Mono.empty();
        }).block();

        assertThat(downstreamExchange.get().getRequest().getHeaders().containsKey(UserContextHeaders.USER_ID))
                .isFalse();
    }

    private TokenService tokenService() {
        return new TokenService() {
            @Override
            public String create(TokenDTO tokenDTO) {
                return "valid-token";
            }

            @Override
            public TokenDTO parse(String token) {
                if (!"valid-token".equals(token)) {
                    return null;
                }
                return new TokenDTO(42L, LocalDateTime.now());
            }
        };
    }

}
