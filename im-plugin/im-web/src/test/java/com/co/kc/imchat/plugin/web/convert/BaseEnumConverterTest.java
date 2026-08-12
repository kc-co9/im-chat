package com.co.kc.imchat.plugin.web.convert;

import com.co.kc.imchat.common.model.enums.BaseEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseEnumConverterTest {

    @Test
    void integerCodeConvertsToBaseEnum() {
        IntegerCodeToBaseEnumConverter<DemoEnum> converter = new IntegerCodeToBaseEnumConverter<>(DemoEnum.class);

        assertThat(converter.convert(1)).isEqualTo(DemoEnum.ENABLED);
    }

    @Test
    void stringCodeConvertsToBaseEnum() {
        StringCodeToBaseEnumConverter<DemoEnum> converter = new StringCodeToBaseEnumConverter<>(DemoEnum.class);

        assertThat(converter.convert("0")).isEqualTo(DemoEnum.DISABLED);
    }

    @Test
    void unknownCodeThrowsException() {
        IntegerCodeToBaseEnumConverter<DemoEnum> converter = new IntegerCodeToBaseEnumConverter<>(DemoEnum.class);

        assertThatThrownBy(() -> converter.convert(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无法匹配对应的枚举类型");
    }

    private enum DemoEnum implements BaseEnum {
        DISABLED(0),
        ENABLED(1);

        private final Integer code;

        DemoEnum(Integer code) {
            this.code = code;
        }

        @Override
        public Integer getCode() {
            return code;
        }
    }
}
