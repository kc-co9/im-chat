package com.co.kc.imchat.domain;

import com.co.kc.imchat.domain.message.ImSystemMessageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImSystemMessageTypeTest {

    @Test
    void groupCreatedBuildsTokenWithSystemPrefix() {
        assertThat(ImSystemMessageType.GROUP_CREATED.token(1001L).getValue())
                .isEqualTo("system:group_created:1001");
    }
}
