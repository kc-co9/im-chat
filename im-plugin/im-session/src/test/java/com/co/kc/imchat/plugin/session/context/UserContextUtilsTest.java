package com.co.kc.imchat.plugin.session.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextUtilsTest {

    @Test
    void storesContextInCurrentThreadOnly() {
        UserContext context = new UserContext(1001L, "user@example.com", "kc");

        UserContextUtils.set(context);

        assertThat(UserContextUtils.get()).isEqualTo(context);
        UserContextUtils.remove();
        assertThat(UserContextUtils.get()).isNull();
    }
}
