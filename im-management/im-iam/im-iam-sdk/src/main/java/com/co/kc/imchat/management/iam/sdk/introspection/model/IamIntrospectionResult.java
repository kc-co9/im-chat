package com.co.kc.imchat.management.iam.sdk.introspection.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.sdk.security.model.IamPrincipal;

import java.time.Instant;
import java.util.Set;

/** IAM Token Introspection 的稳定 SDK 边界结果。 */
public record IamIntrospectionResult(
        Boolean active,
        Long administratorId,
        String username,
        String appKey,
        String clientId,
        Set<String> audiences,
        Set<String> authorities,
        Instant expiresAt,
        /* IAM 生成的可信主体类型，不从 subject 推断。 */
        String principalType,
        /* 按 OAuth scope 语义解析的授权范围。 */
        Set<String> scopes
) {
    public IamIntrospectionResult {
        AssertUtils.argNotNull("IAM introspection active flag must not be null", active);
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        if (active) {
            AssertUtils.allArgNotNull(
                    "Active IAM introspection identity must not be null",
                    administratorId, expiresAt);
            AssertUtils.argNotBlank("Active IAM username must not be blank", username);
            AssertUtils.argNotBlank("Active IAM appKey must not be blank", appKey);
            AssertUtils.argNotBlank("Active IAM clientId must not be blank", clientId);
            AssertUtils.argNotEmpty("Active IAM audiences must not be empty", audiences);
        }
    }

    public static IamIntrospectionResult inactive() {
        return new IamIntrospectionResult(
                false, null, null, null, null, Set.of(), Set.of(), null, null, Set.of());
    }

    public IamPrincipal principal() {
        if (!active) {
            throw new IllegalStateException("Inactive Token does not have an IAM principal");
        }
        return new IamPrincipal(administratorId, username, appKey, authorities);
    }
}
