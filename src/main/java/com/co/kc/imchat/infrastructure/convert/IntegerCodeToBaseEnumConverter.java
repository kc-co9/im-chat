package com.co.kc.imchat.infrastructure.convert;

import com.co.kc.imchat.model.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kc
 */
public class IntegerCodeToBaseEnumConverter<T extends BaseEnum> implements Converter<Integer, T> {
    private final Map<Integer, T> enumMap = new ConcurrentHashMap<>();

    public IntegerCodeToBaseEnumConverter(Class<T> enumType) {
        T[] enums = enumType.getEnumConstants();
        for (T e : enums) {
            enumMap.put(e.getCode(), e);
        }
    }

    @Override
    public T convert(@NotNull Integer code) {
        T t = enumMap.get(code);
        if (Objects.isNull(t)) {
            throw new IllegalArgumentException("无法匹配对应的枚举类型");
        }
        return t;
    }
}
