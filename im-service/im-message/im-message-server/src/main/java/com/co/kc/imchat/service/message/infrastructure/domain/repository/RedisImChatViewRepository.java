package com.co.kc.imchat.service.message.infrastructure.domain.repository;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatView;
import com.co.kc.imchat.service.message.domain.chat.repository.ImChatViewRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/** 基于 Redis 的当前聊天查看状态仓储。 */
@Repository
public class RedisImChatViewRepository implements ImChatViewRepository {
    private static final String KEY_PREFIX = "im:message:presence:chat:";
    private static final Duration PRESENCE_TTL = Duration.ofHours(2);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisImChatViewRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(ImChatView presence) {
        redisTemplate.opsForValue().set(
                key(presence.userId()), presence.chatId().stringValue(), PRESENCE_TTL);
    }

    @Override
    public void clear(UserId userId) {
        redisTemplate.delete(key(userId));
    }

    @Override
    public Optional<ImChatView> find(UserId userId) {
        Object value = redisTemplate.opsForValue().get(key(userId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ImChatView(userId, new ImChatId(Long.valueOf(value.toString()))));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String key(UserId userId) {
        return KEY_PREFIX + userId.stringValue();
    }
}
