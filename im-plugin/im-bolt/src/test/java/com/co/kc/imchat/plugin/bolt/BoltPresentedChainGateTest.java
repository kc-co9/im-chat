package com.co.kc.imchat.plugin.bolt;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static com.co.kc.imchat.plugin.bolt.BoltSecurityGateTestSupport.PresentedWindow;
import static com.co.kc.imchat.plugin.bolt.BoltSecurityGateTestSupport.calculatePresentedWindow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * D10 test-only presented-window and leaf-URI slice, not the complete T1 gate or a validated-path proof.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoltPresentedChainGateTest {
    private BoltSecurityGateTestSupport fixture;

    @BeforeAll
    void openNativeTlsFixture() throws Exception {
        assertThat(System.getProperty("bolt.gate.control", ""))
                .as("supported controls for this bounded slice")
                .isIn("", "leaf-only", "inclusive-not-after", "exclusive-not-before", "omit-presented-root", "skip-leaf-uri", "reuse-channel-facts", "omit-unused-presented");
        fixture = new BoltSecurityGateTestSupport();
    }

    @AfterAll
    void closeNativeTlsFixture() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void allPresentedCertificatesConstrainWindow() throws Exception {
        assertNativeArrays(fixture);
        Instant intermediateExpiry = fixture.intermediate.getNotAfter().toInstant();
        assertThat(intermediateExpiry).isBefore(fixture.leaf.getNotAfter().toInstant());
        for (SSLSession session : List.of(fixture.clientSession, fixture.serverSession)) {
            PresentedWindow window = calculatePresentedWindow(session.getLocalCertificates(), session.getPeerCertificates());
            assertThat(window.earliestNotAfter()).as("presented intermediate, not only leaf, bounds native session").isEqualTo(intermediateExpiry);
            assertThat(window.allows(intermediateExpiry)).as("no new admission at intermediate expiry").isFalse();
        }
        // Computation-only subarrays of real certificates isolate each side; these are not handshake facts.
        Certificate[] leaf = {fixture.leaf};
        Certificate[] chain = {fixture.leaf, fixture.intermediate, fixture.root};
        assertThat(calculatePresentedWindow(chain, leaf).earliestNotAfter()).isEqualTo(intermediateExpiry);
        assertThat(calculatePresentedWindow(leaf, chain).earliestNotAfter()).isEqualTo(intermediateExpiry);
    }

    @Test
    void unsentConfiguredCertificateIsExcluded() throws Exception {
        assertNativeArrays(fixture);
        assertThat(fixture.configuredCertificates()).contains(fixture.extraTrusted);
        Instant unsentExpiry = fixture.extraTrusted.getNotAfter().toInstant();
        for (SSLSession session : List.of(fixture.clientSession, fixture.serverSession)) {
            assertThat(session.getLocalCertificates()).doesNotContain(fixture.extraTrusted);
            assertThat(session.getPeerCertificates()).doesNotContain(fixture.extraTrusted);
            PresentedWindow window = calculatePresentedWindow(session.getLocalCertificates(), session.getPeerCertificates());
            assertThat(window.earliestNotAfter()).isAfter(unsentExpiry);
            assertThat(window.allows(unsentExpiry)).as("unsent configured material does not constrain admission").isTrue();
        }
    }

    @Test
    void presentedRootConstrainsWindow() throws Exception {
        try (BoltSecurityGateTestSupport rootEarlier = new BoltSecurityGateTestSupport(5)) {
            assertNativeArrays(rootEarlier);
            Instant rootExpiry = rootEarlier.root.getNotAfter().toInstant();
            assertThat(rootExpiry).isBefore(rootEarlier.intermediate.getNotAfter().toInstant());
            for (SSLSession session : List.of(rootEarlier.clientSession, rootEarlier.serverSession)) {
                PresentedWindow window = calculatePresentedWindow(session.getLocalCertificates(), session.getPeerCertificates());
                assertThat(window.earliestNotAfter()).as("actually presented root bounds the interval").isEqualTo(rootExpiry);
                assertThat(window.allows(rootExpiry)).as("presented root expiry is exclusive").isFalse();
            }
        }
    }

    @Test
    void windowBoundsUseExplicitInstant() throws Exception {
        PresentedWindow window = calculatePresentedWindow(fixture.clientSession.getLocalCertificates(), fixture.clientSession.getPeerCertificates());
        Instant latest = List.of(fixture.leaf, fixture.intermediate, fixture.root).stream()
                .map(certificate -> certificate.getNotBefore().toInstant()).max(Instant::compareTo).orElseThrow();
        Instant earliest = fixture.intermediate.getNotAfter().toInstant();
        assertThat(window.latestNotBefore()).isEqualTo(latest);
        assertAll("exact explicit-Instant boundaries, no waiting",
                () -> assertThat(window.allows(latest.minusNanos(1))).isFalse(),
                () -> assertThat(window.allows(latest)).as("notBefore equality is allowed").isTrue(),
                () -> assertThat(window.allows(earliest.minusNanos(1))).isTrue(),
                () -> assertThat(window.allows(earliest)).as("notAfter equality is rejected").isFalse(),
                () -> assertThat(window.allows(earliest.plusNanos(1))).isFalse());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFacts")
    void calculationRejectsMissingFacts(String name, Object[] local, Object[] peer) {
        assertThatIllegalArgumentException().as(name)
                .isThrownBy(() -> calculatePresentedWindow(local, peer));
    }

    Stream<Arguments> invalidFacts() {
        // Synthetic containers test pure calculation, never establish authentication facts.
        Object[] valid = {fixture.leaf};
        return Stream.of(Arguments.of("missing local", null, valid), Arguments.of("missing peer", valid, null),
                Arguments.of("empty local", new Object[0], valid), Arguments.of("empty peer", valid, new Object[0]),
                Arguments.of("null local member", new Object[]{null}, valid), Arguments.of("null peer member", valid, new Object[]{null}),
                Arguments.of("wrong local type", new Object[]{"not X509"}, valid), Arguments.of("wrong peer type", valid, new Object[]{"not X509"}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intervalBounds")
    void calculationValidatesIntervalFields(String name, Instant lower, Instant upper, boolean valid) {
        if (valid) {
            assertThat(new PresentedWindow(lower, upper).allows(lower)).as(name).isTrue();
        } else {
            assertThatIllegalArgumentException().as(name)
                    .isThrownBy(() -> new PresentedWindow(lower, upper));
        }
    }

    static Stream<Arguments> intervalBounds() {
        Instant start = Instant.parse("2030-01-01T00:00:00Z");
        return Stream.of(Arguments.of("valid", start, start.plusSeconds(1), true),
                Arguments.of("missing lower", null, start, false), Arguments.of("missing upper", start, null, false),
                Arguments.of("empty", start, start, false), Arguments.of("reversed", start, start.minusSeconds(1), false));
    }

    @Test
    void calculationRequiresExplicitInstant() {
        Instant start = Instant.parse("2030-01-01T00:00:00Z");
        assertThatIllegalArgumentException().isThrownBy(() -> new PresentedWindow(start, start.plusSeconds(1)).allows(null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nativeUriCases")
    void nativeTrustAndStrictLeafUriRejectBeforeForwarding(BoltSecurityGateTestSupport.UriCase scenario) throws Exception {
        try (BoltSecurityGateTestSupport.UriEndpoint endpoint = fixture.uriEndpoint(scenario)) {
            Throwable failure = catchThrowable(endpoint::invoke);
            if (scenario == BoltSecurityGateTestSupport.UriCase.CORRECT_WITH_OTHER_NAMESPACE) {
                assertThat(failure).isNull();
                assertThat(endpoint.attempted.get()).isEqualTo(1);
                assertThat(endpoint.forwarded.get()).isEqualTo(1);
                assertThat(endpoint.encoded.get()).isEqualTo(1);
                assertThat(endpoint.handled.get()).isEqualTo(1);
                BoltSecurityGateTestSupport.SessionFacts facts = endpoint.facts();
                assertThat(facts.peerIdentity()).isEqualTo(BoltSecurityGateTestSupport.URI_IDENTITY);
                assertThat(facts.window().allows(Instant.now())).isTrue();
                for (SSLSession session : endpoint.sessions()) {
                    Certificate[] peer = session.getPeerCertificates();
                    assertThat(((X509Certificate) peer[0]).getSubjectAlternativeNames()).isNotEmpty();
                    for (int index = 1; index < peer.length; index++) {
                        assertThat(((X509Certificate) peer[index]).getSubjectAlternativeNames())
                                .as("CA certificates need no workload URI").isNull();
                    }
                }
            } else {
                assertThat(failure).as("%s must reject before connection delivery", scenario).isNotNull();
                endpoint.assertRejection(failure);
                int expectedAttempts = switch (scenario) {
                    case WRONG_URI, MISSING_URI, MULTIPLE_MATCHING_URI -> 1;
                    default -> 0;
                };
                assertThat(endpoint.attempted.get()).as("URI rejection probes invoke(Connection); failed TLS never delivers").isEqualTo(expectedAttempts);
                assertThat(endpoint.forwarded.get()).as("pre-encoder forwarded boundary").isZero();
                assertThat(endpoint.encoded.get()).as("post-encoder ByteBuf boundary").isZero();
                assertThat(endpoint.handled.get()).isZero();
            }
        }
    }

    static Stream<BoltSecurityGateTestSupport.UriCase> nativeUriCases() {
        return Stream.of(BoltSecurityGateTestSupport.UriCase.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tlsProtocols")
    void eachTlsChannelReacquiresSessionFacts(String protocol) throws Exception {
        try (BoltSecurityGateTestSupport.UriEndpoint endpoint = fixture.protocolEndpoint(protocol)) {
            BoltSecurityGateTestSupport.SessionFacts first = endpoint.acquireNext();
            endpoint.assertCurrentFacts(first, protocol);
            SSLSession firstSession = endpoint.sessions().getFirst();
            BoltSecurityGateTestSupport.SessionFacts second = endpoint.acquireNext();
            assertThat(endpoint.sessions().getFirst()).as("independent native session object").isNotSameAs(firstSession);
            assertThat(second).as("new Channel cannot inherit old immutable facts").isNotSameAs(first);
            assertThat(second.window()).as("current session window is recalculated").isNotSameAs(first.window());
            assertThat(endpoint.acquisitions.get()).as("two actual client session acquisitions").isEqualTo(2);
            endpoint.assertCurrentFacts(second, protocol);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tlsProtocols")
    void resumeOnlyChannelFailsClosedOrReacquiresFacts(String protocol) throws Exception {
        try (BoltSecurityGateTestSupport.UriEndpoint endpoint = fixture.protocolEndpoint(protocol)) {
            BoltSecurityGateTestSupport.SessionFacts first = endpoint.acquireNext();
            endpoint.resumeOnly = true;
            Throwable failure = catchThrowable(endpoint::acquireNext);
            if (failure == null) {
                // Success with session creation disabled is the resumption witness, never session-ID equality.
                assertThat(endpoint.facts()).isNotSameAs(first);
                assertThat(endpoint.acquisitions.get()).isEqualTo(2);
                endpoint.assertCurrentFacts(endpoint.facts(), protocol);
            } else {
                endpoint.assertResumeOnlyRejection(failure);
                assertThat(endpoint.acquisitions.get()).isEqualTo(1);
            }
            assertThat(endpoint.attempted.get()).isZero();
            assertThat(endpoint.forwarded.get()).isZero();
            assertThat(endpoint.encoded.get()).isZero();
            assertThat(endpoint.handled.get()).isZero();
        }
    }

    static Stream<String> tlsProtocols() { return Stream.of("TLSv1.2", "TLSv1.3"); }

    @Test
    void unusedPresentedCertificateConstrainsWindow() throws Exception {
        try (BoltSecurityGateTestSupport.UriEndpoint endpoint = fixture.unusedPresentedEndpoint()) {
            endpoint.invoke();
            for (SSLSession session : endpoint.sessions()) {
                assertThat(session.getLocalCertificates()).contains(fixture.extraTrusted);
                assertThat(session.getPeerCertificates()).contains(fixture.extraTrusted);
                // This unrelated CA cannot issue any member of the known leaf/intermediate/root chain.
                for (X509Certificate member : List.of(fixture.leaf, fixture.intermediate, fixture.root)) {
                    assertThat(member.getIssuerX500Principal()).isNotEqualTo(fixture.extraTrusted.getSubjectX500Principal());
                }
                PresentedWindow window = calculatePresentedWindow(session.getLocalCertificates(), session.getPeerCertificates());
                Instant cutoff = fixture.extraTrusted.getNotAfter().toInstant();
                assertThat(window.earliestNotAfter()).as("unused but actually presented CA constrains D10").isEqualTo(cutoff);
                assertThat(window.allows(cutoff)).isFalse();
            }
        }
    }

    private void assertNativeArrays(BoltSecurityGateTestSupport witness) throws Exception {
        // The support returns sessions only after both native handshake futures succeed.
        List<Certificate> sent = List.of(witness.leaf, witness.intermediate, witness.root);
        for (SSLSession session : List.of(witness.clientSession, witness.serverSession)) {
            assertThat(session.getLocalCertificates()).isNotEmpty().containsExactlyElementsOf(sent);
            assertThat(session.getPeerCertificates()).isNotEmpty().containsExactlyElementsOf(sent);
            assertThat(session.getLocalCertificates()).allMatch(certificate -> certificate instanceof X509Certificate);
            assertThat(session.getPeerCertificates()).allMatch(certificate -> certificate instanceof X509Certificate);
        }
    }
}
