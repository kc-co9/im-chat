package com.co.kc.imchat.common.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.co.kc.imchat.common.model.enums.BaseEnum;

import java.io.IOException;
import java.util.Arrays;

/**
 * https://blog.csdn.net/weixin_41535316/article/details/142426433
 * https://blog.csdn.net/Session_s/article/details/135169587
 *
 * @author kc
 */
public class BaseEnumDeserializer extends StdDeserializer<BaseEnum> implements ContextualDeserializer {
    /**
     * Jackson通过反射创建BaseEnumDeserializer对象，这个对象不会用于正真的反序列化，因为它没有真实的类信息
     * 真正用于反序列化的对象会通过createContextual重新创建
     */
    public BaseEnumDeserializer() {
        this(BaseEnum.class);
    }

    public BaseEnumDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public BaseEnum deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        if (!BaseEnum.class.isAssignableFrom(_valueClass) || !Enum.class.isAssignableFrom(_valueClass)) {
            return null;
        }
        // 反序列目标对象继承自BaseEnum且是枚举类型
        int code = Integer.parseInt(p.getValueAsString());
        return Arrays.stream(_valueClass.getEnumConstants())
                .map(BaseEnum.class::cast)
                .filter(e -> e.getCode().equals(code))
                .findFirst()
                .orElse(null);
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        // 从上下文获取目标对象类型
        final Class<?> rawClass = ctxt.getContextualType().getRawClass();
        // new出来的反序列化器不用我们缓存，一种类型的反序列化器的createContextual方法只会执行一次，执行后的结果Jackson自己会缓存
        return new BaseEnumDeserializer(rawClass);
    }
}
