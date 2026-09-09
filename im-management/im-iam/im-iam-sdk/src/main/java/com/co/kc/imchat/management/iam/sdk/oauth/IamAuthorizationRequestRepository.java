package com.co.kc.imchat.management.iam.sdk.oauth;

import java.util.Optional;

/** 一次性 OAuth2 State 与 PKCE Verifier 存储边界。 */
public interface IamAuthorizationRequestRepository {
    void save(String state, IamAuthorizationRequest request);

    Optional<IamAuthorizationRequest> consume(String state);
}
