package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterConfigurationTest {
    @Test
    void quarantineUsesDedicatedSchedulerAndRejectsAfterShutdown() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new BrokerClusterConfiguration().brokerQuarantineScheduler();
        try {
            assertThat(scheduler.schedule(() -> Thread.currentThread().getName(), 0L, TimeUnit.NANOSECONDS)
                    .get(5, TimeUnit.SECONDS)).startsWith("broker-quarantine-");
            assertThat(scheduler.getRemoveOnCancelPolicy()).isTrue();
            assertThat(scheduler.getExecuteExistingDelayedTasksAfterShutdownPolicy()).isFalse();
        } finally {
            scheduler.shutdown();
            assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThatThrownBy(() -> scheduler.schedule(() -> {}, 0L, TimeUnit.NANOSECONDS))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void peerExecutorBoundsWorkersAndQueueAndNeverRunsRejectedWorkInline() throws Exception {
        BrokerProperties properties = new BrokerProperties();
        properties.getPeerCall().setMaxConcurrentRequests(1);
        properties.getPeerCall().setMaxPendingRequests(1);
        ThreadPoolExecutor executor = new BrokerClusterConfiguration().brokerPeerExecutor(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger queuedCalls = new AtomicInteger();
        AtomicInteger rejectedCalls = new AtomicInteger();
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(queuedCalls::incrementAndGet);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(1);
            assertThat(executor.getQueue()).hasSize(1);
            assertThatThrownBy(() -> executor.execute(rejectedCalls::incrementAndGet))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(queuedCalls).hasValue(0);
            assertThat(rejectedCalls).hasValue(0);
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(queuedCalls).hasValue(1);
    }

    @Test
    void invalidPeerConcurrencyIsRejectedAtConfigurationBoundary() {
        BrokerProperties properties = new BrokerProperties();
        properties.getPeerCall().setMaxConcurrentRequests(0);
        assertThatThrownBy(() -> new BrokerClusterConfiguration().brokerPeerExecutor(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("peer concurrency must be positive");
    }

    @Test
    void invalidPeerQueueCapacityIsRejectedAtConfigurationBoundary() {
        BrokerProperties properties = new BrokerProperties();
        properties.getPeerCall().setMaxPendingRequests(0);
        assertThatThrownBy(() -> new BrokerClusterConfiguration().brokerPeerExecutor(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("peer queue capacity must be positive");
    }
}
