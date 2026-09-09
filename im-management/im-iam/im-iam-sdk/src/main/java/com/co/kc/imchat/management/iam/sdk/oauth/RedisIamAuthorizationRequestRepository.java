package com.co.kc.imchat.management.iam.sdk.oauth;

import com.co.kc.imchat.management.iam.sdk.session.crypto.IamSessionCipher;
import com.co.kc.imchat.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** 通过 Redis GETDEL 原子消费 State，保证 Authorization Code 回调不被重放。 */
@RequiredArgsConstructor
public class RedisIamAuthorizationRequestRepository implements IamAuthorizationRequestRepository {
    private static final String KEY_PREFIX = "im:iam:authorization-request:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final DefaultRedisScript<String> CONSUME =
            new DefaultRedisScript<>("return redis.call('GETDEL', KEYS[1])", String.class);

    private final StringRedisTemplate redisTemplate;
    private final String appKey;
    private final IamSessionCipher cipher;

    @Override
    public void save(String state, IamAuthorizationRequest request) {
        redisTemplate.opsForValue().set(
                key(state),
                cipher.encrypt(JsonUtils.toJson(request)),
                TTL);
    }

    @Override
    public Optional<IamAuthorizationRequest> consume(String state) {
        return Optional.ofNullable(redisTemplate.execute(CONSUME, List.of(key(state))))
                .map(cipher::decrypt)
                .map(value -> JsonUtils.fromJson(value, IamAuthorizationRequest.class));
    }

    private String key(String state) {
        return KEY_PREFIX + appKey + ":" + state;
    }
}
