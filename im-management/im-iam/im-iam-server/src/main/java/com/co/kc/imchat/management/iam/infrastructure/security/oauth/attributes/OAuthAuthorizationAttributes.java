package com.co.kc.imchat.management.iam.infrastructure.security.oauth.attributes;

/** IAM 在 Spring OAuth Authorization 中保存的内部主体与凭据属性名称。 */
public final class OAuthAuthorizationAttributes {
    public static final String PRINCIPAL_TYPE =
            OAuthAuthorizationAttributes.class.getName() + ".principalType";
    public static final String PRINCIPAL =
            OAuthAuthorizationAttributes.class.getName() + ".principal";
    private OAuthAuthorizationAttributes() {
    }
}
