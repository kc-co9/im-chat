package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IamCsrfTokenRepositoryTest {

    @Test
    void publishesConfiguredCookieName() {
        IamCsrfTokenRepository repository = new IamCsrfTokenRepository(
                mock(IamApplicationSessionRepository.class),
                mock(IamSessionCookie.class),
                "IM_ADMIN_XSRF_TOKEN",
                false,
                "Lax",
                Duration.ofHours(1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.publish("csrf-token", response);

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("IM_ADMIN_XSRF_TOKEN=csrf-token;");
    }
}
