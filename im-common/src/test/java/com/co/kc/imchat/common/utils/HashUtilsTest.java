package com.co.kc.imchat.common.utils;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilsTest {

    @Test
    void calculatesSha256FromUtf8Text() {
        byte[] digest = HashUtils.sha256("im-chat");

        assertThat(HexFormat.of().formatHex(digest))
                .isEqualTo("e5d0f99e14fea87386631ce1ba0813fa2e4f140574f2d545a1c0974de0acc644");
    }
}
