package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionException;
import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.plugin.web.support.HttpResultWriter;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionService;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.iam.sdk.security.model.IamPrincipal;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 从加密 BFF 会话实时校验 Access Token，并仅为当前请求构建安全上下文。 */
@RequiredArgsConstructor
public class IamSecurityFilter extends OncePerRequestFilter {
    private final IamSessionCookie sessionCookie;
    private final IamApplicationSessionRepository sessionRepository;
    private final IamIntrospectionService introspectionService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return IamBffRequestPolicy.isPublic(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String sessionId = sessionCookie.read(request).orElse(null);
        if (sessionId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        IamApplicationSession session = sessionRepository.find(sessionId).orElse(null);
        if (session == null) {
            sessionCookie.clear(response);
            filterChain.doFilter(request, response);
            return;
        }
        try {
            IamIntrospectionResult result = introspectionService.introspect(
                    session.tokens().accessToken());
            if (!result.active()) {
                sessionRepository.remove(sessionId);
                sessionCookie.clear(response);
                HttpResultWriter.write(response, HttpErrorCode.AUTH_FAIL);
                return;
            }
            authenticate(result.principal());
            filterChain.doFilter(request, response);
        } catch (IamIntrospectionException exception) {
            HttpResultWriter.write(response, HttpErrorCode.AUTH_FAIL);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(IamPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = principal.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, "N/A", authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
