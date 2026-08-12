package com.co.kc.imchat.plugin.web.convert;

import com.co.kc.imchat.common.model.enums.BaseEnum;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StringCodeToBaseEnumConverterFactory implements ConverterFactory<String, BaseEnum> {

    private static final Map<Class<?>, Converter<String, ? extends BaseEnum>> CONVERTERS = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BaseEnum> Converter<String, T> getConverter(Class<T> targetType) {
        return (Converter<String, T>) CONVERTERS.computeIfAbsent(
                targetType, key -> new StringCodeToBaseEnumConverter<>(targetType));
    }
}
