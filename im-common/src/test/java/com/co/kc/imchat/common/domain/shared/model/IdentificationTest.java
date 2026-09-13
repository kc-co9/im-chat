package com.co.kc.imchat.common.domain.shared.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentificationTest {

    @Test
    void exposesOnlyValidatedPersistenceMetadataSetters() {
        Identification identification = new Identification();
        identification.setPkId(11L);
        identification.setRowVersion(2L);

        assertThatThrownBy(() -> identification.setPkId(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据库主键ID不能为空");
        assertThat(Arrays.stream(Identification.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("restorePersistenceState");
    }
}
