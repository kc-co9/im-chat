package com.co.kc.imchat.gateway.ws.security.authentication;

import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import com.co.kc.imchat.plugin.session.token.TokenDTO;
import com.co.kc.imchat.plugin.session.token.TokenService;

/**
 * WS 认证管理器。
 * <p>
 * 负责把握手阶段提取到的 token 转换为网关内部的认证身份。
 */
public class WsAuthenticationManager {
    private final TokenService tokenService;

    public WsAuthenticationManager(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * 根据握手令牌认证用户身份。
     *
     * @param token 握手令牌
     * @return 认证身份；无法认证时返回 null
     */
    public WsPrincipal authenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TokenDTO tokenDTO = tokenService.parse(token);
        if (tokenDTO == null || tokenDTO.getUserId() == null) {
            return null;
        }
        return new WsPrincipal(tokenDTO.getUserId());
    }
}
