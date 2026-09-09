package com.co.kc.imchat.management.iam.domain.session.service;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipal;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import lombok.RequiredArgsConstructor;

/** 管理需要访问管理员聚合的 OAuth 授权主体规则。 */
@RequiredArgsConstructor
public class OAuthAuthorizationService {
    private final AdministratorRepository administratorRepository;

    /**
     * 校验并规范化 OAuth 授权主体。
     *
     * @param requestedPrincipal OAuth 协议提交的授权主体
     * @param client             已校验的 OAuth 客户端
     * @return 规范化后的授权主体
     */
    public OAuthPrincipal principal(OAuthPrincipal requestedPrincipal, OAuthClient client) {
        if (requestedPrincipal.type() == OAuthPrincipalType.CLIENT) {
            return new OAuthPrincipal(OAuthPrincipalType.CLIENT, client.getClientId().value());
        }
        if (requestedPrincipal.type() != OAuthPrincipalType.ADMINISTRATOR) {
            throw new AuthException("管理员认证无效");
        }
        try {
            Administrator administrator = administratorRepository
                    .find(new AdministratorId(Long.valueOf(requestedPrincipal.value())))
                    .filter(Administrator::isActive)
                    .orElseThrow(() -> new AuthException("管理员认证无效"));
            return new OAuthPrincipal(OAuthPrincipalType.ADMINISTRATOR, administrator.getId().value().toString());
        } catch (NumberFormatException exception) {
            throw new AuthException("管理员认证无效");
        }
    }

}
