package com.co.kc.imchat.support.utils;

import java.util.UUID;

/**
 * 生成器
 *
 * @author kc
 */
public class GeneratorUtils {
    private GeneratorUtils() {
    }

    /**
     * 生成UUID字符串
     *
     * @return UUID
     */
    public static String nextUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
