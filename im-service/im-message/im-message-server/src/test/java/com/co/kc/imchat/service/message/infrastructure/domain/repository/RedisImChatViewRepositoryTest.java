package com.co.kc.imchat.service.message.infrastructure.domain.repository;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RedisImChatViewRepositoryTest {
    @Test
    void savesChatIdWithUserScopedTtl() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RedisImChatViewRepository repository = new RedisImChatViewRepository(redis);

        repository.save(new ImChatView(new UserId(1L), new ImChatId(101L)));

        verify(values).set(eq("im:message:presence:chat:1"), eq("101"), any(Duration.class));
    }

    @Test
    void findsAndClearsPresence() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("im:message:presence:chat:1")).thenReturn("101");
        RedisImChatViewRepository repository = new RedisImChatViewRepository(redis);

        Optional<ImChatView> result = repository.find(new UserId(1L));
        repository.clear(new UserId(1L));

        assertThat(result).contains(new ImChatView(new UserId(1L), new ImChatId(101L)));
        verify(redis).delete("im:message:presence:chat:1");
    }
}
