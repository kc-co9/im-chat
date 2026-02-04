package com.kim.omgchat.infrastructure.convert;

import com.kim.omgchat.model.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kc
 */
public class StringCodeToBaseEnumConverter<T extends BaseEnum> implements Converter<String, T> {
    private final Map<String, T> enumMap = new ConcurrentHashMap<>();

    public StringCodeToBaseEnumConverter(Class<T> enumType) {
        T[] enums = enumType.getEnumConstants();
        for (T e : enums) {
            enumMap.put(String.valueOf(e.getCode()), e);
        }
    }

    @Override
    public T convert(@NotNull String code) {
        T t = enumMap.get(code);
        if (Objects.isNull(t)) {
            throw new IllegalArgumentException("无法匹配对应的枚举类型");
        }
        return t;
    }
}
