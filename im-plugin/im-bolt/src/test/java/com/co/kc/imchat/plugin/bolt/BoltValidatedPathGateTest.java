package com.co.kc.imchat.plugin.bolt;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Native TLS observations only: passing this witness does NOT satisfy the validated-path gate.
 * The extra trusted fixture is a certificate entry, not an alternate private-key alias.
 */
class BoltValidatedPathGateTest {
    @Test
    void nativeTlsExposesSentChainsButPeerArrayIsNotValidatedPath() throws Exception {
        String control = System.getProperty("bolt.gate.control", "");
        assertThat(control).as("supported test-only controls").isIn("", "peer-array");
        Path directory;
        try (BoltSecurityGateTestSupport witness = new BoltSecurityGateTestSupport()) {
            directory = witness.directory;
            List<Certificate> selected = List.of(witness.leaf, witness.intermediate, witness.root);
            assertThat(witness.clientSession.getLocalCertificates()).containsExactlyElementsOf(selected);
            assertThat(witness.serverSession.getPeerCertificates()).containsExactlyElementsOf(selected);
            assertThat(witness.serverSession.getLocalCertificates()).containsExactlyElementsOf(selected);
            assertThat(witness.clientSession.getPeerCertificates()).containsExactlyElementsOf(selected);
            assertThat(selected).doesNotContain(witness.extraTrusted);
            assertThat(witness.extraTrusted.getNotAfter()).isBefore(witness.intermediate.getNotAfter());
            assertThat(witness.intermediate.getNotAfter()).isBefore(witness.leaf.getNotAfter());
            assertThat(witness.configuredCertificates()).contains(witness.extraTrusted);

            if (control.equals("peer-array")) {
                // Deliberately false O2 assumption: blindly treating every presented certificate
                // as non-anchor path material retains the configured root. Intermediate
                // is trusted too: neither the actual path nor its anchor is established.
                assertThat(List.of(witness.clientSession.getPeerCertificates()).contains(witness.root))
                        .as("peer-array is NOT the O2 validated non-anchor path: presented root must be excluded")
                        .isFalse();
            }
            // No validated CertPath is claimed or synthesized here. Native session arrays prove
            // selected/sent material, not which path/anchor the internal TrustManager used.
        }
        assertThat(Files.exists(directory)).as("temporary certificate material deleted").isFalse();
    }
}
