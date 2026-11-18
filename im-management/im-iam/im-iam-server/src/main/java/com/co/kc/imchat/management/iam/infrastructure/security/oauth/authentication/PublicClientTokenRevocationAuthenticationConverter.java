package com.co.kc.imchat.management.iam.infrastructure.security.oauth.authentication;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Map;

/** 仅在标准撤销端点提取 public Client 身份。 */
public final class PublicClientTokenRevocationAuthenticationConverter implements AuthenticationConverter {
    /** 标准 OAuth 撤销端点匹配器。 */
    private final RequestMatcher endpointMatcher;

    /** 绑定标准撤销端点匹配器，避免该认证方式扩散到其他 OAuth 端点。 */
    public PublicClientTokenRevocationAuthenticationConverter(RequestMatcher endpointMatcher) {
        this.endpointMatcher = endpointMatcher;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        // 步骤一：只处理撤销端点，并拒绝同时出现其他 Client 认证材料的请求。
        if (!endpointMatcher.matches(request) || request.getHeader("Authorization") != null
                || request.getParameter("client_secret") != null) {
            return null;
        }
        String[] clientIds = request.getParameterValues("client_id");
        if (clientIds == null) {
            return null;
        }
        if (clientIds.length != 1 || clientIds[0].isBlank()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        // 步骤二：将单一 client_id 转换为仅供撤销流程识别的认证类型。
        return new RevocationClientAuthenticationToken(clientIds[0]);
    }

    /** 独立类型不被复用于换码或其他端点。 */
    static final class RevocationClientAuthenticationToken extends OAuth2ClientAuthenticationToken {
        RevocationClientAuthenticationToken(String clientId) {
            super(clientId, ClientAuthenticationMethod.NONE, null, Map.of());
        }
    }
}
