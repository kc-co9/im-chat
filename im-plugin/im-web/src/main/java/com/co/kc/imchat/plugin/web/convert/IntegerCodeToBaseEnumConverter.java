package com.co.kc.imchat.plugin.web.convert;

import com.co.kc.imchat.common.model.enums.BaseEnum;
import org.springframework.core.convert.converter.Converter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class IntegerCodeToBaseEnumConverter<T extends BaseEnum> implements Converter<Integer, T> {

    private final Map<Integer, T> enumMap = new ConcurrentHashMap<>();

    public IntegerCodeToBaseEnumConverter(Class<T> enumType) {
        T[] enums = enumType.getEnumConstants();
        for (T item : enums) {
            enumMap.put(item.getCode(), item);
        }
    }

    @Override
    public T convert(Integer code) {
        T item = enumMap.get(code);
        if (Objects.isNull(item)) {
            throw new IllegalArgumentException("无法匹配对应的枚举类型");
        }
        return item;
    }
}
