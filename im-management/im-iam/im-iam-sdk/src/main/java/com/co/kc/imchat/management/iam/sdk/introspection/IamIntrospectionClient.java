package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;

/** IAM Token Introspection 远程协议边界。 */
public interface IamIntrospectionClient {
    IamIntrospectionResult introspect(String accessToken);
}
