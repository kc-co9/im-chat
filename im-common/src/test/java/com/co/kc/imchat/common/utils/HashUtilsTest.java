package com.co.kc.imchat.common.utils;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilsTest {

    @Test
    void calculatesSha256FromUtf8Text() {
        byte[] digest = HashUtils.sha256("im-chat");

        assertThat(HexFormat.of().formatHex(digest))
                .isEqualTo("e5d0f99e14fea87386631ce1ba0813fa2e4f140574f2d545a1c0974de0acc644");
    }

    @Test
    void calculatesSha256FromBytes() {
        byte[] digest = HashUtils.sha256(new byte[]{1, 2, 3});

        assertThat(HexFormat.of().formatHex(digest))
                .isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
    }

    @Test
    void calculatesStableSha256HexFromLongSequence() {
        assertThat(HashUtils.sha256HexLongs(List.of(101L, 102L)))
                .isEqualTo("20720334cce432efa48fcbaf05b7b0b63e8c0ac57f813f219596e6bf842ee0bd");
        assertThat(HashUtils.sha256HexLongs(List.of()))
                .isEqualTo("af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc");
    }
}
