package com.co.kc.imchat.management.ops.cli.auth;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class CliLoginSessionTest {

    @Test
    void callbackAtLoginDeadlineIsRejected() {
        Instant startedAt = Instant.parse("2026-11-02T00:00:00Z");
        Instant deadline = startedAt.plusSeconds(120);
        try (MockedStatic<Instant> time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(startedAt);
            CliLoginSession session = CliLoginSession.start();
            time.when(Instant::now).thenReturn(deadline);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> session.acceptAuthorizationCode(session.state(), "late-code"));

            assertEquals("Authorization login expired", failure.getMessage());
        }
    }

    @Test
    void pkceChallengeBindsIndependentVerifierUsingS256() throws Exception {
        CliLoginSession session = CliLoginSession.start();
        CliLoginSession another = CliLoginSession.start();

        assertTrue(session.verifier().matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(session.state(), session.verifier());
        assertNotEquals(session.verifier(), another.verifier());
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(session.verifier().getBytes(StandardCharsets.US_ASCII));
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(digest), session.challenge());
    }

    @Test
    void eachLoginGeneratesIndependentUrlSafeState() {
        CliLoginSession first = CliLoginSession.start();
        CliLoginSession second = CliLoginSession.start();

        assertTrue(first.state().matches("[A-Za-z0-9_-]{43}"), "state must encode 32 random bytes");
        assertTrue(second.state().matches("[A-Za-z0-9_-]{43}"), "state must encode 32 random bytes");
        assertNotEquals(first.state(), second.state());
    }

    @Test
    void emptyCodeDoesNotConsumePendingLogin() {
        CliLoginSession session = CliLoginSession.start();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> session.acceptAuthorizationCode(session.state(), " "));

        assertEquals("Authorization code is required", failure.getMessage());
        assertEquals("valid-code", session.acceptAuthorizationCode(session.state(), "valid-code"));
    }

    @Test
    void successfulCallbackCanOnlyBeConsumedOnce() {
        CliLoginSession session = CliLoginSession.start();

        assertEquals("first-code", session.acceptAuthorizationCode(session.state(), "first-code"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> session.acceptAuthorizationCode(session.state(), "replayed-code"));

        assertEquals("Authorization callback already consumed", failure.getMessage());
    }

    @Test
    void mismatchedStateRejectsAuthorizationCode() {
        CliLoginSession session = CliLoginSession.start();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> session.acceptAuthorizationCode("different-login-state", "callback-code"));

        assertEquals("Authorization state mismatch", failure.getMessage());
        assertEquals("valid-code", session.acceptAuthorizationCode(session.state(), "valid-code"));
    }
}
