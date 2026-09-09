package com.co.kc.imchat.management.iam.support.restriction;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamLoginProperties;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/** 使用 Redis 限制连续认证失败的管理员登录。 */
public class AuthenticationRestriction {
    private static final String FAILURE_KEY_PREFIX = "im:iam:login:failures:";
    private static final String RESTRICTION_KEY_PREFIX = "im:iam:login:restricted:";
    private static final String AUTHENTICATION_FAILED = "账号或密码错误";
    private static final RedisScript<Long> FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local failures = redis.call('INCR', KEYS[1])
            if failures == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if failures >= tonumber(ARGV[1]) then
                redis.call('SET', KEYS[2], '1', 'PX', ARGV[2])
                redis.call('DEL', KEYS[1])
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String failureLimit;
    private final String restrictionMillis;

    public AuthenticationRestriction(
            StringRedisTemplate redisTemplate,
            IamLoginProperties properties
    ) {
        AssertUtils.allArgNotNull(
                "login protection dependencies must not be null",
                redisTemplate,
                properties);
        this.redisTemplate = redisTemplate;
        this.failureLimit = properties.failureLimit().toString();
        this.restrictionMillis = Long.toString(properties.lockDuration().toMillis());
    }

    /** 拒绝仍处于临时限制期内的管理员登录。 */
    public void ensureAllowed(AdministratorId administratorId) {
        if (isRestricted(administratorId)) {
            throw new AuthException(AUTHENTICATION_FAILED);
        }
    }

    /**
     * 记录一次认证失败。
     *
     * @return 本次失败是否触发临时限制
     */
    public boolean failed(AdministratorId administratorId) {
        Long result = redisTemplate.execute(
                FAILURE_SCRIPT,
                keys(administratorId),
                failureLimit,
                restrictionMillis);
        if (result == null) {
            throw new IllegalStateException("Login protection returned no result");
        }
        return result == 1L;
    }

    /** 清理管理员的临时登录保护状态。 */
    @AfterTransactionCommit
    public void reset(AdministratorId administratorId) {
        redisTemplate.delete(keys(administratorId));
    }

    /** 查询管理员是否处于临时登录限制期。 */
    public boolean isRestricted(AdministratorId administratorId) {
        AssertUtils.argNotNull("administrator id must not be null", administratorId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(restrictionKey(administratorId)));
    }

    private List<String> keys(AdministratorId administratorId) {
        AssertUtils.argNotNull("administrator id must not be null", administratorId);
        return List.of(failureKey(administratorId), restrictionKey(administratorId));
    }

    private String failureKey(AdministratorId administratorId) {
        return FAILURE_KEY_PREFIX + administratorId.value();
    }

    private String restrictionKey(AdministratorId administratorId) {
        return RESTRICTION_KEY_PREFIX + administratorId.value();
    }
}
