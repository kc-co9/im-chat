package com.co.kc.imchat.interfaces.support.web.convert;

import com.co.kc.imchat.common.model.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kc
 */
public class StringCodeToBaseEnumConverterFactory implements ConverterFactory<String, BaseEnum> {
    private static final Map<Class<?>, Converter<String, ? extends BaseEnum>> CONVERTERS = new ConcurrentHashMap<>();

    @Override
    public <T extends BaseEnum> Converter<String, T> getConverter(@NotNull Class<T> targetType) {
        //noinspection unchecked
        return (Converter<String, T>) CONVERTERS.computeIfAbsent(targetType, k -> new StringCodeToBaseEnumConverter<>(targetType));
    }
}
