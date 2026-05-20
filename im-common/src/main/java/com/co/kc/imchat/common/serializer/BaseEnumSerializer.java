package com.co.kc.imchat.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.co.kc.imchat.common.model.enums.BaseEnum;

import java.io.IOException;

/**
 * @author kc
 */
public class BaseEnumSerializer extends JsonSerializer<BaseEnum> {
    @Override
    public void serialize(BaseEnum value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumber(value.getCode());
    }
}
