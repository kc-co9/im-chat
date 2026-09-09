package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;

/** 健康时始终实时查询 IAM，仅在可用性故障时使用五分钟内的成功缓存。 */
@RequiredArgsConstructor
public class IamIntrospectionService {
    private final IamIntrospectionClient client;
    private final IamIntrospectionCache cache;
    private final IamAvailabilityCircuit circuit;
    private final String appKey;
    private final String clientId;
    private final Duration staleTtl;

    @Observed(name = "im.iam.introspection")
    public IamIntrospectionResult introspect(String accessToken) {
        return introspect(accessToken, Instant.now());
    }

    IamIntrospectionResult introspect(String accessToken, Instant now) {
        if (!circuit.allowRequest(now)) {
            return cached(accessToken, now);
        }
        try {
            IamIntrospectionResult result = client.introspect(accessToken);
            circuit.success();
            if (!trusted(result, now)) {
                cache.remove(accessToken, appKey);
                return IamIntrospectionResult.inactive();
            }
            cache.save(accessToken, appKey, result, now);
            return result;
        } catch (IamIntrospectionException exception) {
            if (!exception.isAvailabilityFailure()) {
                circuit.rejected();
                cache.remove(accessToken, appKey);
                throw exception;
            }
            circuit.availabilityFailure(now);
            return cached(accessToken, now);
        }
    }

    private IamIntrospectionResult cached(String accessToken, Instant now) {
        return cache.find(accessToken, appKey)
                .filter(entry -> !entry.verifiedAt().plus(staleTtl).isBefore(now))
                .map(IamIntrospectionCache.Entry::result)
                .filter(result -> trusted(result, now))
                .orElseGet(IamIntrospectionResult::inactive);
    }

    private boolean trusted(IamIntrospectionResult result, Instant now) {
        return result.active()
                && appKey.equals(result.appKey())
                && clientId.equals(result.clientId())
                && result.audiences().contains(appKey)
                && result.expiresAt().isAfter(now);
    }
}
