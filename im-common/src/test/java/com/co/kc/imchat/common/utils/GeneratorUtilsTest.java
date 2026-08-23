package com.co.kc.imchat.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratorUtilsTest {

    @Test
    void generatesIndependentUrlSafeRandomIds() {
        String first = GeneratorUtils.nextRandomId(32);
        String second = GeneratorUtils.nextRandomId(32);

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void rejectsNonPositiveByteLength() {
        assertThatThrownBy(() -> GeneratorUtils.nextRandomId(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("byteLength");
    }
}
