package com.co.kc.imchat.management.iam.sdk.session.repository;

import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;
import com.co.kc.imchat.management.iam.sdk.session.crypto.IamSessionCipher;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

/** 使用应用隔离 Redis 命名空间存储加密 BFF 会话。 */
@RequiredArgsConstructor
public class RedisIamApplicationSessionRepository implements IamApplicationSessionRepository {
    private static final String KEY_PREFIX = "im:iam:application-session:";
    private static final DefaultRedisScript<Long> REPLACE_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1]); "
                    + "if not current then return 0 end; "
                    + "local value = cjson.decode(current); "
                    + "if value.updatedAt ~= ARGV[1] then return 0 end; "
                    + "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2]); return 1;",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IamSessionCipher cipher;
    private final String appKey;
    private final Duration sessionTtl;

    @Override
    public Optional<IamApplicationSession> find(String sessionId) {
        String value = redisTemplate.opsForValue().get(key(sessionId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            StoredSession stored = objectMapper.readValue(value, StoredSession.class);
            return Optional.of(new IamApplicationSession(
                    sessionId,
                    new IamTokenSet(
                            cipher.decrypt(stored.accessToken()),
                            stored.accessTokenExpiresAt(),
                            cipher.decrypt(stored.refreshToken()),
                            stored.refreshTokenExpiresAt()),
                    stored.csrfToken(),
                    stored.createdAt(),
                    stored.updatedAt()));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            remove(sessionId);
            throw new IllegalStateException("IAM application Session cannot be restored", exception);
        }
    }

    @Override
    public void save(IamApplicationSession session) {
        try {
            Duration ttl = ttl(session);
            if (ttl.isNegative() || ttl.isZero()) {
                remove(session.sessionId());
                return;
            }
            redisTemplate.opsForValue().set(
                    key(session.sessionId()), serialized(session), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("IAM application Session cannot be persisted", exception);
        }
    }

    @Override
    public boolean replace(IamApplicationSession expected, IamApplicationSession replacement) {
        try {
            Duration ttl = ttl(replacement);
            if (ttl.isNegative() || ttl.isZero()) {
                return false;
            }
            Long replaced = redisTemplate.execute(
                    REPLACE_SCRIPT,
                    List.of(key(expected.sessionId())),
                    expected.updatedAt().toString(),
                    serialized(replacement),
                    Long.toString(ttl.toMillis()));
            return Long.valueOf(1L).equals(replaced);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("IAM application Session cannot be persisted", exception);
        }
    }

    @Override
    public void remove(String sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    private String key(String sessionId) {
        return KEY_PREFIX + appKey + ":" + sessionId;
    }

    private Duration ttl(IamApplicationSession session) {
        Duration refreshRemaining = Duration.between(
                Instant.now(), session.tokens().refreshTokenExpiresAt());
        return refreshRemaining.compareTo(sessionTtl) < 0 ? refreshRemaining : sessionTtl;
    }

    private String serialized(IamApplicationSession session) throws JsonProcessingException {
        StoredSession stored = new StoredSession(
                cipher.encrypt(session.tokens().accessToken()),
                session.tokens().accessTokenExpiresAt(),
                cipher.encrypt(session.tokens().refreshToken()),
                session.tokens().refreshTokenExpiresAt(),
                session.csrfToken(),
                session.createdAt(),
                session.updatedAt());
        return objectMapper.writeValueAsString(stored);
    }

    private record StoredSession(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            String csrfToken,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
