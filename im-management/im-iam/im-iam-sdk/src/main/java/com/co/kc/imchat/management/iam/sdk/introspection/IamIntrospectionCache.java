package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;

import java.time.Instant;
import java.util.Optional;

/** 仅保存最后一次成功 active Introspection 结果的失效缓存。 */
public interface IamIntrospectionCache {
    Optional<Entry> find(String accessToken, String appKey);

    void save(String accessToken, String appKey, IamIntrospectionResult result, Instant verifiedAt);

    void remove(String accessToken, String appKey);

    record Entry(IamIntrospectionResult result, Instant verifiedAt) {
    }
}
