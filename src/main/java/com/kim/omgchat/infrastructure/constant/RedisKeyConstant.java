package com.kim.omgchat.infrastructure.constant;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 18:03
 */
public class RedisKeyConstant {
    public static String generateRedisKey(String prefix, String key) {
        return prefix + ":" + key;
    }

    public static String generateOnlineUserKey(String key) {
        return generateRedisKey(RedisKeyPrefixConstant.ONLINE_USER_KEY_PREFIX, key);
    }

    public static String generateUserTokenKey(String key) {
        return generateRedisKey(RedisKeyPrefixConstant.USER_TOKEN_KEY_PREFIX, key);
    }
}
