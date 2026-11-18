package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.plugin.web.support.HttpResultWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 仅在宿主显式声明的 CLI 安全链内建立管理员身份，不接受 Cookie 替代或混用。
 */
public class IamAdministratorBearerFilter extends OncePerRequestFilter {
    /** 执行 IAM 实时 introspection 的认证器。 */
    private final IamAdministratorBearerAuthenticator authenticator;
    /** 用于拒绝 Bearer 与 BFF Cookie 混用的会话读取器。 */
    private final IamSessionCookie sessionCookie;
    /** Spring 默认 Bearer 解析器。 */
    private final DefaultBearerTokenResolver tokenResolver = new DefaultBearerTokenResolver();

    /** 绑定实时管理员认证器和用于拒绝 Cookie 混用的会话 Cookie 读取器。 */
    public IamAdministratorBearerFilter(
            IamAdministratorBearerAuthenticator authenticator,
            IamSessionCookie sessionCookie
    ) {
        this.authenticator = authenticator;
        this.sessionCookie = sessionCookie;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // 步骤一：只接受 Bearer，发现 Cookie 或缺失 Token 时立即拒绝。
            String token = tokenResolver.resolve(request);
            if (token == null || sessionCookie.read(request).isPresent()) {
                reject(response);
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            try {
                // 步骤二：认证成功后只在当前请求建立安全上下文。
                context.setAuthentication(authenticator.authenticate(token));
            } catch (AuthenticationException exception) {
                reject(response);
                return;
            }
            SecurityContextHolder.setContext(context);
            // 步骤三：交给宿主安全链继续处理业务和权限。
            filterChain.doFilter(request, response);
        } catch (OAuth2AuthenticationException exception) {
            reject(response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        // 统一输出认证失败协议，避免泄露 Token 校验细节。
        HttpResultWriter.write(response, HttpErrorCode.AUTH_FAIL);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
