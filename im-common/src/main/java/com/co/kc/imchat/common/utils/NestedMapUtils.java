package com.co.kc.imchat.common.utils;

import java.util.Map;

public class NestedMapUtils {

    private NestedMapUtils() {
    }

    /**
     * 删除二级 Map 中的指定值；如果二级 Map 删除后为空，则同步移除一级 Map 的 key。
     */
    public static <K1, K2, V> void prune(Map<K1, Map<K2, V>> map, K1 firstKey, K2 secondKey) {
        Map<K2, V> nestedMap = map.get(firstKey);
        if (nestedMap == null) {
            return;
        }
        nestedMap.remove(secondKey);
        if (nestedMap.isEmpty()) {
            map.remove(firstKey, nestedMap);
        }
    }
}
