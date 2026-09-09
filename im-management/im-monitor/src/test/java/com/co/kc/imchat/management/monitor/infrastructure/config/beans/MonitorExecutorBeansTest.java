package com.co.kc.imchat.management.monitor.infrastructure.config.beans;

import com.co.kc.imchat.management.monitor.infrastructure.config.properties.MonitorQueryProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitorExecutorBeansTest {

    @Test
    void rejectsWorkWhenWorkersAndQueueAreSaturated() throws Exception {
        MonitorExecutorBeans beans = new MonitorExecutorBeans();
        ExecutorService executor = beans.monitorQueryExecutor(new MonitorQueryProperties(1, 1));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> awaitRelease(started, release));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {
            });

            assertThatThrownBy(() -> executor.execute(() -> {
            })).isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitRelease(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
