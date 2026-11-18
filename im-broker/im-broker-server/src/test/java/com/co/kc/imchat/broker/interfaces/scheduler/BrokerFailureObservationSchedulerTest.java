package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerFailureObservationSchedulerTest {
    @Test
    void failedObservationTickDoesNotPreventNextScheduledInvocation() {
        BrokerClusterAppService app = mock(BrokerClusterAppService.class);
        when(app.reconcileFailures()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("quorum lost")),
                CompletableFuture.completedFuture(null));
        BrokerFailureObservationScheduler scheduler = new BrokerFailureObservationScheduler(app);
        assertThatCode(scheduler::observe).doesNotThrowAnyException();
        scheduler.observe();
        verify(app, times(2)).reconcileFailures();
    }

    @Test
    void synchronousProbeRejectionDoesNotPreventNextScheduledInvocation() {
        BrokerClusterAppService app = mock(BrokerClusterAppService.class);
        when(app.probeFailures()).thenThrow(new IllegalStateException("executor rejected"))
                .thenReturn(CompletableFuture.completedFuture(null));
        BrokerFailureProbeScheduler scheduler = new BrokerFailureProbeScheduler(app);
        assertThatCode(scheduler::probe).doesNotThrowAnyException();
        scheduler.probe();
        verify(app, times(2)).probeFailures();
    }
}
