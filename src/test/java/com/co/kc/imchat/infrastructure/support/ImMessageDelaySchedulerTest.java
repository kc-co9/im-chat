package com.co.kc.imchat.infrastructure.support;

import com.co.kc.imchat.support.notifier.task.NotifierTask;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.util.Date;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ImMessageDelaySchedulerTest {

    @Test
    void scheduleRunsTaskConsumerAtNextTime() {
        ImmediateTaskScheduler taskScheduler = new ImmediateTaskScheduler();
        AtomicInteger consumedTaskCount = new AtomicInteger();
        ImMessageDelayScheduler scheduler =
                new ImMessageDelayScheduler(taskScheduler, task -> consumedTaskCount.incrementAndGet());
        NotifierTask task = new NotifierTask();
        task.setNextAtMillis(123L);
        Date startTime = new Date(123L);

        scheduler.schedule(task);

        assertThat(taskScheduler.scheduledStartTime).isEqualTo(startTime);
        assertThat(consumedTaskCount.get()).isEqualTo(1);
    }

    private static class ImmediateTaskScheduler implements TaskScheduler {
        private Date scheduledStartTime;

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Date startTime) {
            this.scheduledStartTime = startTime;
            task.run();
            return new CompletedScheduledFuture();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Date startTime, long period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Date startTime, long delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
            throw new UnsupportedOperationException();
        }
    }

    private static class CompletedScheduledFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0L;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
