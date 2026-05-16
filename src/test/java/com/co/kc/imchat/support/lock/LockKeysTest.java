package com.co.kc.imchat.support.lock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LockKeysTest {

    @Test
    void userPairBuildsStableKeyRegardlessOfArgumentOrder() {
        assertThat(LockKeys.userPair(2L, 1L)).isEqualTo("1:2");
        assertThat(LockKeys.userPair(1L, 2L)).isEqualTo("1:2");
    }
}
