package com.co.kc.imchat.management.iam.domain.application.service;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthRawClientSecret;

/** OAuth2 客户端密钥单向编码能力。 */
@FunctionalInterface
public interface OAuthClientSecretService {
    OAuthClientSecret encode(OAuthRawClientSecret secret);
}
