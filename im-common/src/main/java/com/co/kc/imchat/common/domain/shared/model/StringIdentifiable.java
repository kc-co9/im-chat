package com.co.kc.imchat.common.domain.shared.model;

/**
 * 具有稳定字符串表示的领域标识。
 */
public interface StringIdentifiable {

    /**
     * 返回用于边界传输、日志和键构造的字符串值。
     *
     * @return 字符串形式的标识
     */
    String stringValue();
}
