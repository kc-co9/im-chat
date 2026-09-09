package com.co.kc.imchat.management.iam.sdk.oauth;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;

/**
 * OAuth2 登录回调完成后的应用 Session 与站内恢复地址。
 */
public record IamAuthorizationCompletion(
        IamApplicationSession session,
        String continuePath
) {
    public IamAuthorizationCompletion {
        AssertUtils.argNotNull("IAM application session must not be null", session);
        AssertUtils.argNotBlank("continue path must not be blank", continuePath);
    }
}
