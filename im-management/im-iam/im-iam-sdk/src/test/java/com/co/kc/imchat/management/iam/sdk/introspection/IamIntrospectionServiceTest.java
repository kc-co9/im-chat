package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamIntrospectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");
    private static final String TOKEN = "opaque-access-token";

    @Test
    void callsLiveIntrospectionForEveryHealthyRequest() {
        IamIntrospectionClient client = mock(IamIntrospectionClient.class);
        IamIntrospectionCache cache = mock(IamIntrospectionCache.class);
        when(client.introspect(TOKEN)).thenReturn(active());
        IamIntrospectionService service = service(client, cache);

        assertThat(service.introspect(TOKEN, NOW).active()).isTrue();
        assertThat(service.introspect(TOKEN, NOW.plusSeconds(1)).active()).isTrue();

        verify(client, times(2)).introspect(TOKEN);
        verify(cache, times(2)).save(
                org.mockito.ArgumentMatchers.eq(TOKEN),
                org.mockito.ArgumentMatchers.eq("imAdmin"),
                org.mockito.ArgumentMatchers.eq(active()),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usesFreshLastSuccessOnlyForAvailabilityFailure() {
        IamIntrospectionClient client = mock(IamIntrospectionClient.class);
        IamIntrospectionCache cache = mock(IamIntrospectionCache.class);
        when(client.introspect(TOKEN)).thenThrow(new IamIntrospectionException(
                "timeout", true, null));
        when(cache.find(TOKEN, "imAdmin")).thenReturn(Optional.of(
                new IamIntrospectionCache.Entry(active(), NOW.minusSeconds(299))));

        assertThat(service(client, cache).introspect(TOKEN, NOW).active()).isTrue();
    }

    @Test
    void deniesStaleCacheAndNeverFallsBackForExplicitRejection() {
        IamIntrospectionClient unavailable = mock(IamIntrospectionClient.class);
        IamIntrospectionCache cache = mock(IamIntrospectionCache.class);
        when(unavailable.introspect(TOKEN)).thenThrow(new IamIntrospectionException(
                "timeout", true, null));
        when(cache.find(TOKEN, "imAdmin")).thenReturn(Optional.of(
                new IamIntrospectionCache.Entry(active(), NOW.minusSeconds(301))));
        assertThat(service(unavailable, cache).introspect(TOKEN, NOW).active()).isFalse();

        IamIntrospectionClient rejected = mock(IamIntrospectionClient.class);
        when(rejected.introspect(TOKEN)).thenThrow(new IamIntrospectionException(
                "unauthorized", false, null));
        assertThatThrownBy(() -> service(rejected, cache).introspect(TOKEN, NOW))
                .isInstanceOf(IamIntrospectionException.class);
        verify(cache).remove(TOKEN, "imAdmin");
    }

    @Test
    void invalidOrCrossApplicationResponseDoesNotUseCache() {
        IamIntrospectionClient client = mock(IamIntrospectionClient.class);
        IamIntrospectionCache cache = mock(IamIntrospectionCache.class);
        when(client.introspect(TOKEN)).thenReturn(new IamIntrospectionResult(
                true, 1L, "root", "imAdmin", "im-admin-client",
                Set.of("imMonitor"),
                Set.of("monitor.overview.read"), NOW.plusSeconds(900)));

        assertThat(service(client, cache).introspect(TOKEN, NOW).active()).isFalse();
        verify(cache).remove(TOKEN, "imAdmin");
        verify(cache, never()).find(TOKEN, "imAdmin");
    }

    private static IamIntrospectionService service(
            IamIntrospectionClient client,
            IamIntrospectionCache cache
    ) {
        return new IamIntrospectionService(
                client,
                cache,
                new IamAvailabilityCircuit(),
                "imAdmin",
                "im-admin-client",
                Duration.ofMinutes(5));
    }

    private static IamIntrospectionResult active() {
        return new IamIntrospectionResult(
                true,
                1L,
                "root",
                "imAdmin",
                "im-admin-client",
                Set.of("imAdmin"),
                Set.of("admin.user.read"),
                NOW.plusSeconds(900));
    }
}
