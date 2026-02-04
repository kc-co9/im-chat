package com.co.kc.imchat.infrastructure.convert;

import com.co.kc.imchat.model.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kc
 */
public class IntegerCodeToBaseEnumConverterFactory implements ConverterFactory<Integer, BaseEnum> {
    private static final Map<Class<?>, Converter<Integer, ? extends BaseEnum>> CONVERTERS = new ConcurrentHashMap<>();

    @Override
    public <T extends BaseEnum> Converter<Integer, T> getConverter(@NotNull Class<T> targetType) {
        //noinspection unchecked
        return (Converter<Integer, T>) CONVERTERS.computeIfAbsent(targetType, k -> new IntegerCodeToBaseEnumConverter<>(targetType));
    }
}
