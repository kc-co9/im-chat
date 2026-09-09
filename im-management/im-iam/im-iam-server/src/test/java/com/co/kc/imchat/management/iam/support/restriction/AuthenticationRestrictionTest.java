package com.co.kc.imchat.management.iam.support.restriction;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamLoginProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationRestrictionTest {
    private static final AdministratorId ADMINISTRATOR_ID = new AdministratorId(11L);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final AuthenticationRestriction protection = new AuthenticationRestriction(
            redisTemplate,
            new IamLoginProperties(5, Duration.ofMinutes(15)));

    @Test
    void rejectsTemporarilyRestrictedLogin() {
        when(redisTemplate.hasKey("im:iam:login:restricted:11")).thenReturn(true);

        assertThatThrownBy(() -> protection.ensureAllowed(ADMINISTRATOR_ID))
                .isInstanceOf(AuthException.class)
                .extracting("reason")
                .isEqualTo("账号或密码错误");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsWhenFailureReachesRestrictionThreshold() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(
                        "im:iam:login:failures:11",
                        "im:iam:login:restricted:11")),
                eq("5"),
                eq("900000")))
                .thenReturn(1L);

        assertThat(protection.failed(ADMINISTRATOR_ID)).isTrue();
    }

    @Test
    void clearsTemporaryStateAfterSuccessfulLogin() {
        protection.reset(ADMINISTRATOR_ID);

        verify(redisTemplate).delete(List.of(
                "im:iam:login:failures:11",
                "im:iam:login:restricted:11"));
    }

    @Test
    void allowsLoginWithoutTemporaryRestriction() {
        when(redisTemplate.hasKey("im:iam:login:restricted:11")).thenReturn(false);

        assertThatCode(() -> protection.ensureAllowed(ADMINISTRATOR_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        when(redisTemplate.hasKey("im:iam:login:restricted:11"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatThrownBy(() -> protection.ensureAllowed(ADMINISTRATOR_ID))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
