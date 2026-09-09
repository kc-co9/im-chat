package com.co.kc.imchat.management.iam.interfaces.security;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthRawClientSecret;
import com.co.kc.imchat.management.iam.infrastructure.domain.service.BcryptOAuthClientSecretService;
import com.co.kc.imchat.management.iam.infrastructure.config.beans.IamSecurityBeans;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamAuthorizationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamSecurityBeansTest {

    @Test
    void namesServerSecurityFilterChainsByTheirRequestBoundary() {
        assertThat(Arrays.stream(IamSecurityBeans.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .contains(
                        "oauthSecurityFilterChain",
                        "iamClientApiSecurityFilterChain",
                        "iamWebSecurityFilterChain")
                .doesNotContain(
                        "authorizationServerSecurityFilterChain",
                        "applicationSecurityFilterChain",
                        "oauthTokenIntrospectionProvider",
                        "oauthCredentialReuseProvider");
    }

    @Test
    void usesSpringTokenGeneratorCompositionWithoutProjectWrapper() {
        assertThatCode(() -> Class.forName(
                "org.springframework.security.oauth2.server.authorization.token."
                        + "DelegatingOAuth2TokenGenerator"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Class.forName(
                "com.co.kc.imchat.management.iam.infrastructure.security.oauth."
                        + "CompositeOAuthTokenGenerator"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void usesOAuthClientSecretPasswordEncoder() {
        IamSecurityBeans config = new IamSecurityBeans();
        OAuthRawClientSecret rawSecret =
                new OAuthRawClientSecret("strong-client-secret-value");
        OAuthClientSecret encodedSecret =
                new BcryptOAuthClientSecretService().encode(rawSecret);

        PasswordEncoder passwordEncoder = config.oauthClientSecretPasswordEncoder();

        assertThat(passwordEncoder.matches(rawSecret.value(), encodedSecret.value())).isTrue();
    }

    @Test
    void publishesIamWebCsrfTokenThroughReadableSameOriginCookie() {
        IamSecurityBeans config = new IamSecurityBeans();
        CookieCsrfTokenRepository repository = config.iamWebCsrfTokenRepository();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(
                new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-token"),
                new MockHttpServletRequest(),
                response);

        assertThat(response.getCookie("XSRF-TOKEN")).isNotNull().satisfies(cookie -> {
            assertThat(cookie.isHttpOnly()).isFalse();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getValue()).isEqualTo("csrf-token");
        });
    }

    @Test
    void acceptsReadableCookieTokenFromTheLoginFormParameter() {
        IamSecurityBeans config = new IamSecurityBeans();
        CsrfTokenRequestHandler handler = config.iamWebCsrfTokenRequestHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("_csrf", "csrf-token");
        DefaultCsrfToken token = new DefaultCsrfToken(
                "X-XSRF-TOKEN",
                "_csrf",
                "csrf-token");

        assertThat(handler.resolveCsrfTokenValue(request, token))
                .isEqualTo("csrf-token");
    }

    @Test
    void forwardsTheLoginPageToTheIamUiWithoutAController() {
        IamSecurityBeans config = new IamSecurityBeans();
        WebMvcConfigurer webMvcConfigurer = config.iamLoginPageWebMvcConfigurer();
        ViewControllerRegistry registry = mock(ViewControllerRegistry.class);
        ViewControllerRegistration registration = mock(ViewControllerRegistration.class);
        when(registry.addViewController("/login")).thenReturn(registration);

        webMvcConfigurer.addViewControllers(registry);

        verify(registration).setViewName("forward:/index.html");
    }

    @Test
    void exposesTheApprovedOAuth2AndOidcEndpoints() {
        IamSecurityBeans config = new IamSecurityBeans();
        AuthorizationServerSettings settings = config.authorizationServerSettings(
                new IamAuthorizationProperties(
                        URI.create("https://iam.example.com"),
                        "file:/etc/im-chat/iam-signing.p12",
                        "store-password",
                        "iam-signing",
                        "key-password",
                        Duration.ofHours(8),
                        Duration.ofHours(24)));

        assertThat(settings.getAuthorizationEndpoint()).isEqualTo("/oauth2/authorize");
        assertThat(settings.getTokenEndpoint()).isEqualTo("/oauth2/token");
        assertThat(settings.getTokenIntrospectionEndpoint()).isEqualTo("/oauth2/introspect");
        assertThat(settings.getTokenRevocationEndpoint()).isEqualTo("/oauth2/revoke");
        assertThat(settings.getOidcUserInfoEndpoint()).isEqualTo("/userinfo");
        assertThat(settings.getOidcLogoutEndpoint()).isEqualTo("/connect/logout");
        assertThat(settings.getIssuer()).isEqualTo("https://iam.example.com");
    }
}
