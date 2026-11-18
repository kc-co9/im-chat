package com.co.kc.imchat.management.iam.infrastructure.security.oauth.attributes;

/** IAM 在 Spring OAuth Authorization 中保存的内部主体与凭据属性名称。 */
public final class OAuthAuthorizationAttributes {
    public static final String PRINCIPAL_TYPE =
            OAuthAuthorizationAttributes.class.getName() + ".principalType";
    public static final String PRINCIPAL =
            OAuthAuthorizationAttributes.class.getName() + ".principal";
    /* 内部原读版本，不进入 Token Claim 或持久化属性 JSON。 */
    public static final String ROW_VERSION =
            OAuthAuthorizationAttributes.class.getName() + ".rowVersion";
    private OAuthAuthorizationAttributes() {
    }
}
