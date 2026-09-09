package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.common.utils.HashUtils;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/** 使用 Token SHA-256 摘要与 appKey 作为键的 Redis Introspection 缓存。 */
@RequiredArgsConstructor
public class RedisIamIntrospectionCache implements IamIntrospectionCache {
    private static final String KEY_PREFIX = "im:iam:introspection:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration staleTtl;

    @Override
    public Optional<Entry> find(String accessToken, String appKey) {
        String value = redisTemplate.opsForValue().get(key(accessToken, appKey));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, Entry.class));
        } catch (JsonProcessingException exception) {
            remove(accessToken, appKey);
            return Optional.empty();
        }
    }

    @Override
    public void save(
            String accessToken,
            String appKey,
            IamIntrospectionResult result,
            Instant verifiedAt
    ) {
        if (!result.active()) {
            return;
        }
        Duration tokenRemaining = Duration.between(verifiedAt, result.expiresAt());
        Duration ttl = tokenRemaining.compareTo(staleTtl) < 0 ? tokenRemaining : staleTtl;
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    key(accessToken, appKey),
                    objectMapper.writeValueAsString(new Entry(result, verifiedAt)),
                    ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("IAM Introspection cache cannot be encoded", exception);
        }
    }

    @Override
    public void remove(String accessToken, String appKey) {
        redisTemplate.delete(key(accessToken, appKey));
    }

    String key(String accessToken, String appKey) {
        byte[] digest = HashUtils.sha256(accessToken);
        return KEY_PREFIX + appKey + ":" + HexFormat.of().formatHex(digest);
    }
}
