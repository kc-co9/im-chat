package com.co.kc.imchat.common.domain.shared.model;

import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringIdentifiableTest {

    @Test
    void exposesStableStringValueForCommonIdentifiers() {
        assertThat(new UserId(42L).stringValue()).isEqualTo("42");
        assertThat(new GroupId(7L).stringValue()).isEqualTo("7");
    }
}
