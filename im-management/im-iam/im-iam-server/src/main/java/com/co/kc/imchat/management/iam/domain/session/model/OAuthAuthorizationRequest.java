package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;

import java.util.Set;

/** 浏览器授权请求中需要随授权生命周期保存的状态。 */
public record OAuthAuthorizationRequest(
        /* OAuth 授权端点地址。 */
        String authorizationUri,
        /* 客户端回调地址。 */
        String redirectUri,
        /* 防止请求伪造的状态值。 */
        String state,
        /* PKCE code challenge。 */
        String codeChallenge,
        /* PKCE challenge 算法。 */
        String codeChallengeMethod,
        /* 请求的授权范围。 */
        Set<OAuthScope> scopes
) {
    public OAuthAuthorizationRequest {
        AssertUtils.domainPropNotBlank("authorization URI must not be blank", authorizationUri);
        AssertUtils.domainPropNotBlank("redirect URI must not be blank", redirectUri);
        AssertUtils.domainPropNotBlank("code challenge must not be blank", codeChallenge);
        AssertUtils.domainPropNotBlank("code challenge method must not be blank", codeChallengeMethod);
        AssertUtils.domainPropNotEmpty("requested scopes must not be empty", scopes);
        scopes = Set.copyOf(scopes);
    }
}
