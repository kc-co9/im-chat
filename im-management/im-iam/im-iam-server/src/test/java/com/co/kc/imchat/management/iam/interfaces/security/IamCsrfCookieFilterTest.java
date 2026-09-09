package com.co.kc.imchat.management.iam.interfaces.security;

import com.co.kc.imchat.management.iam.infrastructure.security.web.IamCsrfCookieFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamCsrfCookieFilterTest {

    @Test
    void resolvesDeferredTokenBeforeContinuingTheFilterChain() throws Exception {
        CsrfToken csrfToken = mock(CsrfToken.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CsrfToken.class.getName(), csrfToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(csrfToken.getToken()).thenReturn("csrf-token");

        new IamCsrfCookieFilter().doFilter(request, response, filterChain);

        verify(csrfToken).getToken();
        verify(filterChain).doFilter(request, response);
    }
}
